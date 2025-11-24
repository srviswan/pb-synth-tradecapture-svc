package com.pb.synth.tradecapture.service;

import com.pb.synth.tradecapture.model.EnrichmentStatus;
import com.pb.synth.tradecapture.model.TradeCaptureRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Service for enriching trade data with reference data.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EnrichmentService {

    private final SecurityMasterServiceClient securityMasterServiceClient;
    private final AccountServiceClient accountServiceClient;

    /**
     * Enrich trade data with security and account information.
     */
    public EnrichmentResult enrich(TradeCaptureRequest request) {
        EnrichmentStatus status = EnrichmentStatus.COMPLETE;
        Map<String, Object> enrichedData = Map.of(); // Simplified for now
        
        try {
            // Enrich with security data
            var securityData = securityMasterServiceClient.lookupSecurity(request.getSecurityId());
            if (securityData.isEmpty()) {
                log.warn("Security not found: {}", request.getSecurityId());
                status = EnrichmentStatus.PARTIAL;
            }
            
            // Enrich with account data
            var accountData = accountServiceClient.lookupAccount(request.getAccountId(), request.getBookId());
            if (accountData.isEmpty()) {
                log.warn("Account not found: {} / {}", request.getAccountId(), request.getBookId());
                status = EnrichmentStatus.PARTIAL;
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

