package com.pb.synth.tradecapture.controller;

import com.pb.synth.tradecapture.model.TradeCaptureRequest;
import com.pb.synth.tradecapture.model.TradeCaptureResponse;
import com.pb.synth.tradecapture.service.TradeCaptureService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for trade capture operations.
 */
@RestController
@RequestMapping("/api/v1/trades")
@RequiredArgsConstructor
@Slf4j
public class TradeCaptureController {

    private final TradeCaptureService tradeCaptureService;

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
     */
    @PostMapping("/capture/async")
    public ResponseEntity<TradeCaptureResponse> captureTradeAsync(
            @Valid @RequestBody TradeCaptureRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        
        // Use header idempotency key if provided
        if (idempotencyKey != null && request.getIdempotencyKey() == null) {
            request.setIdempotencyKey(idempotencyKey);
        }
        
        log.info("Processing async trade capture request: {}", request.getTradeId());
        // TODO: Implement async processing
        TradeCaptureResponse response = tradeCaptureService.processTrade(request);
        
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * Get SwapBlotter by trade ID.
     */
    @GetMapping("/capture/{tradeId}")
    public ResponseEntity<TradeCaptureResponse> getSwapBlotter(
            @PathVariable String tradeId) {
        
        // TODO: Implement retrieval
        return ResponseEntity.notFound().build();
    }
}

