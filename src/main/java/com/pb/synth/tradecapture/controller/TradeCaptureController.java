package com.pb.synth.tradecapture.controller;

import com.pb.synth.tradecapture.model.TradeCaptureRequest;
import com.pb.synth.tradecapture.model.TradeCaptureResponse;
import com.pb.synth.tradecapture.service.TradeCaptureService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for trade capture operations.
 */
@RestController
@RequestMapping("/api/v1/trades")
@RequiredArgsConstructor
@Slf4j
public class TradeCaptureController {

    private final TradeCaptureService tradeCaptureService;
    private final com.pb.synth.tradecapture.service.SwapBlotterService swapBlotterService;
    private final ApplicationContext applicationContext;

    /**
     * Capture and enrich a single trade (synchronous).
     */
    @PostMapping("/capture")
    public ResponseEntity<TradeCaptureResponse> captureTrade(
            @Valid @RequestBody TradeCaptureRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        
        // Use header idempotency key if provided
        if (idempotencyKey != null && request.getIdempotencyKey() == null) {
            request.setIdempotencyKey(idempotencyKey);
        }
        
        log.info("Processing trade capture request: {}", request.getTradeId());
        TradeCaptureResponse response = tradeCaptureService.processTrade(request);
        
        HttpStatus status = "SUCCESS".equals(response.getStatus()) 
            ? HttpStatus.OK 
            : "DUPLICATE".equals(response.getStatus())
                ? HttpStatus.OK
                : HttpStatus.INTERNAL_SERVER_ERROR;
        
        return ResponseEntity.status(status).body(response);
    }

    /**
     * Capture trade asynchronously.
     * Returns 202 Accepted immediately with job ID.
     */
    @PostMapping("/capture/async")
    public ResponseEntity<Map<String, Object>> captureTradeAsync(
            @Valid @RequestBody TradeCaptureRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        
        // Use header idempotency key if provided
        if (idempotencyKey != null && request.getIdempotencyKey() == null) {
            request.setIdempotencyKey(idempotencyKey);
        }
        
        log.info("Submitting async trade capture request: {}", request.getTradeId());
        
        // Submit for async processing
        var asyncTradeProcessingService = 
            applicationContext.getBean(com.pb.synth.tradecapture.service.AsyncTradeProcessingService.class);
        String jobId = asyncTradeProcessingService.submitAsyncTrade(request);
        
        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .body(Map.of(
                "jobId", jobId,
                "status", "ACCEPTED",
                "message", "Trade submitted for async processing",
                "statusUrl", "/api/v1/trades/jobs/" + jobId + "/status"
            ));
    }
    
    /**
     * Get async job status.
     */
    @GetMapping("/jobs/{jobId}/status")
    public ResponseEntity<com.pb.synth.tradecapture.model.AsyncJobStatus> getJobStatus(
            @PathVariable("jobId") String jobId) {
        
        try {
            var asyncTradeProcessingService = 
                applicationContext.getBean(com.pb.synth.tradecapture.service.AsyncTradeProcessingService.class);
            com.pb.synth.tradecapture.model.AsyncJobStatus status = 
                asyncTradeProcessingService.getJobStatus(jobId);
            
            return ResponseEntity.ok(status);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * Cancel an async job.
     */
    @DeleteMapping("/jobs/{jobId}")
    public ResponseEntity<Map<String, Object>> cancelJob(@PathVariable("jobId") String jobId) {
        var asyncTradeProcessingService = 
            applicationContext.getBean(com.pb.synth.tradecapture.service.AsyncTradeProcessingService.class);
        
        boolean cancelled = asyncTradeProcessingService.cancelJob(jobId);
        
        if (cancelled) {
            return ResponseEntity.ok(Map.of(
                "jobId", jobId,
                "status", "CANCELLED",
                "message", "Job cancelled successfully"
            ));
        } else {
            return ResponseEntity.badRequest().body(Map.of(
                "jobId", jobId,
                "status", "NOT_CANCELLABLE",
                "message", "Job cannot be cancelled (may be completed, failed, or not found)"
            ));
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

