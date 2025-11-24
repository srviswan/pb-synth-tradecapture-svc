package com.pb.synth.tradecapture.service;

import com.pb.synth.tradecapture.model.EnrichmentStatus;
import com.pb.synth.tradecapture.model.TradeCaptureRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Service for enriching trade data with reference data.
 * Supports parallel enrichment operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EnrichmentService {

    private final SecurityMasterServiceClient securityMasterServiceClient;
    private final AccountServiceClient accountServiceClient;

    /**
     * Enrich trade data with security and account information.
     * Uses parallel enrichment for better performance.
     */
    public EnrichmentResult enrich(TradeCaptureRequest request) {
        EnrichmentStatus status = EnrichmentStatus.COMPLETE;
        Map<String, Object> enrichedData = new HashMap<>();
        
        try {
            // Parallel enrichment - fetch security and account data concurrently
            CompletableFuture<Map<String, Object>> securityFuture = 
                securityMasterServiceClient.lookupSecurityAsync(request.getSecurityId())
                    .thenApply(opt -> opt.orElse(Map.of()));
            
            CompletableFuture<Map<String, Object>> accountFuture = 
                accountServiceClient.lookupAccountAsync(request.getAccountId(), request.getBookId())
                    .thenApply(opt -> opt.orElse(Map.of()));
            
            // Wait for both to complete
            CompletableFuture.allOf(securityFuture, accountFuture).join();
            
            Map<String, Object> securityData = securityFuture.getNow(Map.of());
            Map<String, Object> accountData = accountFuture.getNow(Map.of());
            
            if (securityData.isEmpty()) {
                log.warn("Security not found: {}", request.getSecurityId());
                status = EnrichmentStatus.PARTIAL;
            } else {
                enrichedData.put("security", securityData);
            }
            
            if (accountData.isEmpty()) {
                log.warn("Account not found: {} / {}", request.getAccountId(), request.getBookId());
                status = EnrichmentStatus.PARTIAL;
            } else {
                enrichedData.put("account", accountData);
            }
            
            if (securityData.isEmpty() && accountData.isEmpty()) {
                status = EnrichmentStatus.FAILED;
            }
            
            return EnrichmentResult.builder()
                .status(status)
                .enrichedData(enrichedData)
                .build();
        } catch (Exception e) {
            log.error("Error enriching trade data", e);
            return EnrichmentResult.builder()
                .status(EnrichmentStatus.FAILED)
                .enrichedData(enrichedData)
                .build();
        }
    }

    @lombok.Data
    @lombok.Builder
    public static class EnrichmentResult {
        private EnrichmentStatus status;
        private Map<String, Object> enrichedData;
    }
}

