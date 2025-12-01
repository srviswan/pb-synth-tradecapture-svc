package com.pb.synth.tradecapture.controller;

import com.pb.synth.tradecapture.model.AsyncJobStatus;
import com.pb.synth.tradecapture.model.TradeCaptureRequest;
import com.pb.synth.tradecapture.model.TradeCaptureResponse;
import com.pb.synth.tradecapture.service.JobStatusService;
import com.pb.synth.tradecapture.service.SwapBlotterService;
import com.pb.synth.tradecapture.service.TradePublishingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for trade capture operations.
 * All trade creation endpoints publish to message queue for async processing.
 */
@RestController
@RequestMapping("/api/v1/trades")
@RequiredArgsConstructor
@Slf4j
public class TradeCaptureController {

    private final TradePublishingService tradePublishingService;
    private final JobStatusService jobStatusService;
    private final SwapBlotterService swapBlotterService;

    /**
     * Capture and enrich a single trade (async via queue).
     * Publishes to message queue and returns 202 Accepted with job ID.
     * 
     * @param request The trade capture request
     * @param idempotencyKey Optional idempotency key header
     * @param callbackUrl Required webhook callback URL for completion notification
     * @return 202 Accepted with job ID
     */
    @PostMapping("/capture")
    public ResponseEntity<Map<String, Object>> captureTrade(
            @Valid @RequestBody TradeCaptureRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Callback-Url", required = true) String callbackUrl) {
        
        // Use header idempotency key if provided
        if (idempotencyKey != null && request.getIdempotencyKey() == null) {
            request.setIdempotencyKey(idempotencyKey);
        }
        
        log.info("Publishing trade capture request to queue: {}", request.getTradeId());
        
        // Create job and publish to queue
        String jobId = jobStatusService.createJob(null, request.getTradeId(), "REST_API");
        tradePublishingService.publishTrade(request, jobId, "REST_API", callbackUrl);
        
        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .body(Map.of(
                "jobId", jobId,
                "status", "ACCEPTED",
                "message", "Trade submitted for processing",
                "statusUrl", "/api/v1/trades/jobs/" + jobId + "/status"
            ));
    }
    
    /**
     * Get async job status.
     */
    @GetMapping("/jobs/{jobId}/status")
    public ResponseEntity<AsyncJobStatus> getJobStatus(
            @PathVariable("jobId") String jobId) {
        
        try {
            AsyncJobStatus status = jobStatusService.getJobStatus(jobId);
            return ResponseEntity.ok(status);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * Cancel an async job (if still pending).
     * Note: Once a job is in the queue and being processed, it cannot be cancelled.
     */
    @DeleteMapping("/jobs/{jobId}")
    public ResponseEntity<Map<String, Object>> cancelJob(@PathVariable("jobId") String jobId) {
        try {
            AsyncJobStatus jobStatus = jobStatusService.getJobStatus(jobId);
            
            if (jobStatus.getStatus() == AsyncJobStatus.JobStatus.PENDING) {
                jobStatusService.updateJobStatus(jobId, AsyncJobStatus.JobStatus.CANCELLED, 
                    0, "Job cancelled by user");
                
                return ResponseEntity.ok(Map.of(
                    "jobId", jobId,
                    "status", "CANCELLED",
                    "message", "Job cancelled successfully"
                ));
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                    "jobId", jobId,
                    "status", "NOT_CANCELLABLE",
                    "message", "Job cannot be cancelled (status: " + jobStatus.getStatus() + ")"
                ));
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get SwapBlotter by trade ID.
     */
    @GetMapping("/capture/{tradeId}")
    public ResponseEntity<TradeCaptureResponse> getSwapBlotter(
            @PathVariable("tradeId") String tradeId) {
        
        log.info("Retrieving SwapBlotter for trade ID: {}", tradeId);
        
        try {
            var swapBlotterOpt = swapBlotterService.getSwapBlotterByTradeId(tradeId);
            
            if (swapBlotterOpt.isPresent()) {
                var swapBlotter = swapBlotterOpt.get();
                var response = TradeCaptureResponse.builder()
                    .tradeId(tradeId)
                    .status("SUCCESS")
                    .swapBlotter(swapBlotter)
                    .build();
                
                return ResponseEntity.ok(response);
            } else {
                log.warn("SwapBlotter not found for trade ID: {}", tradeId);
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("Error retrieving SwapBlotter for trade ID: {}", tradeId, e);
            var response = TradeCaptureResponse.builder()
                .tradeId(tradeId)
                .status("FAILED")
                .error(com.pb.synth.tradecapture.model.ErrorDetail.builder()
                    .code("RETRIEVAL_ERROR")
                    .message("Failed to retrieve trade: " + e.getMessage())
                    .timestamp(java.time.ZonedDateTime.now())
                    .build())
                .build();
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}

