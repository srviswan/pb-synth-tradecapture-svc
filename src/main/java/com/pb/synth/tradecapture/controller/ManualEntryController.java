package com.pb.synth.tradecapture.controller;

import com.pb.synth.tradecapture.model.TradeCaptureRequest;
import com.pb.synth.tradecapture.model.TradeCaptureResponse;
import com.pb.synth.tradecapture.model.TradeSource;
import com.pb.synth.tradecapture.service.TradeCaptureService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for manual trade entry.
 */
@RestController
@RequestMapping("/api/v1/trades")
@RequiredArgsConstructor
@Slf4j
public class ManualEntryController {

    private final TradeCaptureService tradeCaptureService;

    /**
     * Manual trade entry endpoint.
     */
    @PostMapping("/manual-entry")
    public ResponseEntity<TradeCaptureResponse> manualEntry(
            @Valid @RequestBody TradeCaptureRequest request) {
        
        // Ensure source is MANUAL
        request.setSource(TradeSource.MANUAL);
        
        log.info("Processing manual trade entry: {}", request.getTradeId());
        TradeCaptureResponse response = tradeCaptureService.processTrade(request);
        
        return ResponseEntity.ok(response);
    }
}

