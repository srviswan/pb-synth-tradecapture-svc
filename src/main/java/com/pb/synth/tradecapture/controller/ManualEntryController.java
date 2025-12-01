package com.pb.synth.tradecapture.controller;

import com.pb.synth.tradecapture.model.TradeCaptureRequest;
import com.pb.synth.tradecapture.model.TradeSource;
import com.pb.synth.tradecapture.service.JobStatusService;
import com.pb.synth.tradecapture.service.QuickValidationService;
import com.pb.synth.tradecapture.service.TradePublishingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST controller for manual trade entry.
 * Performs immediate validation, then publishes to queue for async processing.
 */
@RestController
@RequestMapping("/api/v1/trades")
@RequiredArgsConstructor
@Slf4j
public class ManualEntryController {

    private final QuickValidationService quickValidationService;
    private final TradePublishingService tradePublishingService;
    private final JobStatusService jobStatusService;

    /**
     * Manual trade entry endpoint with immediate validation and async processing.
     * 
     * @param request The trade capture request
     * @param callbackUrl Required webhook callback URL for completion notification
     * @return 202 Accepted with job ID if validation passes, 400 Bad Request if validation fails
     */
    @PostMapping("/manual-entry")
    public ResponseEntity<Map<String, Object>> manualEntry(
            @Valid @RequestBody TradeCaptureRequest request,
            @RequestHeader(value = "X-Callback-Url", required = true) String callbackUrl) {
        
        // Ensure source is MANUAL
        request.setSource(TradeSource.MANUAL);
        
        log.info("Processing manual trade entry: {}", request.getTradeId());
        
        // Immediate validation
        QuickValidationService.ValidationResult validation = 
            quickValidationService.validateQuick(request);
        
        if (!validation.isPassed()) {
            // Return 400 with validation errors
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "VALIDATION_FAILED");
            errorResponse.put("message", "Trade validation failed");
            errorResponse.put("validation", Map.of(
                "passed", false,
                "errors", validation.getErrors().stream()
                    .map(error -> Map.of(
                        "field", error.getField(),
                        "message", error.getMessage()
                    ))
                    .collect(Collectors.toList())
            ));
            
            log.warn("Manual entry validation failed: tradeId={}, errors={}", 
                request.getTradeId(), validation.getErrors().size());
            
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }
        
        // Validation passed - publish to queue
        String jobId = jobStatusService.createJob(null, request.getTradeId(), "MANUAL_ENTRY");
        tradePublishingService.publishTrade(request, jobId, "MANUAL_ENTRY", callbackUrl);
        
        Map<String, Object> response = new HashMap<>();
        response.put("jobId", jobId);
        response.put("status", "ACCEPTED");
        response.put("message", "Trade submitted for processing");
        response.put("validation", Map.of(
            "passed", true,
            "errors", List.of()
        ));
        response.put("statusUrl", "/api/v1/trades/jobs/" + jobId + "/status");
        
        log.info("Manual entry accepted and queued: tradeId={}, jobId={}", 
            request.getTradeId(), jobId);
        
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}

