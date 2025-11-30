package com.pb.synth.tradecapture.service;

import com.pb.synth.tradecapture.model.EnrichmentStatus;
import com.pb.synth.tradecapture.model.TradeCaptureRequest;
import com.pb.synth.tradecapture.service.cache.ReferenceDataCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
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
    private final ReferenceDataCacheService referenceDataCacheService;

    /**
     * Enrich trade data with security and account information.
     * Uses Redis cache (L1) first, then external services (L2) as fallback.
     * Uses parallel enrichment for better performance.
     */
    public EnrichmentResult enrich(TradeCaptureRequest request) {
        EnrichmentStatus status = EnrichmentStatus.COMPLETE;
        Map<String, Object> enrichedData = new HashMap<>();
        
        try {
            // Try cache first (L1)
            Optional<Map<String, Object>> cachedSecurity = referenceDataCacheService.getSecurity(request.getSecurityId());
            Optional<Map<String, Object>> cachedAccount = referenceDataCacheService.getAccount(
                request.getAccountId(), request.getBookId());
            
            CompletableFuture<Map<String, Object>> securityFuture;
            CompletableFuture<Map<String, Object>> accountFuture;
            
            if (cachedSecurity.isPresent()) {
                log.debug("Security cache hit: {}", request.getSecurityId());
                securityFuture = CompletableFuture.completedFuture(cachedSecurity.get());
            } else {
                // Fallback to external service (L2)
                securityFuture = securityMasterServiceClient.lookupSecurityAsync(request.getSecurityId())
                    .thenApply(opt -> {
                        opt.ifPresent(data -> referenceDataCacheService.putSecurity(request.getSecurityId(), data));
                        return opt.orElse(Map.of());
                    });
            }
            
            if (cachedAccount.isPresent()) {
                log.debug("Account cache hit: {} / {}", request.getAccountId(), request.getBookId());
                accountFuture = CompletableFuture.completedFuture(cachedAccount.get());
            } else {
                // Fallback to external service (L2)
                accountFuture = accountServiceClient.lookupAccountAsync(request.getAccountId(), request.getBookId())
                    .thenApply(opt -> {
                        opt.ifPresent(data -> referenceDataCacheService.putAccount(
                            request.getAccountId(), request.getBookId(), data));
                        return opt.orElse(Map.of());
                    });
            }
            
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

