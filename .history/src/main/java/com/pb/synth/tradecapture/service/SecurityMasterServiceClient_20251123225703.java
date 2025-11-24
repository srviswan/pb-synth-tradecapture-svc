package com.pb.synth.tradecapture.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Client for SecurityMasterService with circuit breaker and retry.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SecurityMasterServiceClient {

    private final RestTemplate restTemplate;

    @Value("${services.security-master.url}")
    private String baseUrl;

    @Value("${services.security-master.timeout:5000}")
    private int timeout;

    /**
     * Lookup security by security ID with circuit breaker and retry.
     */
    @SuppressWarnings("unchecked")
    @CircuitBreaker(name = "securityMasterService", fallbackMethod = "lookupSecurityFallback")
    @Retry(name = "securityMasterService")
    @TimeLimiter(name = "securityMasterService")
    public CompletableFuture<Optional<Map<String, Object>>> lookupSecurityAsync(String securityId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = baseUrl + "/api/v1/securities/" + securityId;
                Map<String, Object> response = restTemplate.getForObject(url, Map.class);
                log.debug("Successfully looked up security: {}", securityId);
                return Optional.ofNullable(response);
            } catch (Exception e) {
                log.error("Error looking up security: {}", securityId, e);
                throw new RuntimeException("Failed to lookup security", e);
            }
        });
    }
    
    /**
     * Synchronous version for backward compatibility.
     */
    public Optional<Map<String, Object>> lookupSecurity(String securityId) {
        try {
            return lookupSecurityAsync(securityId).get();
        } catch (Exception e) {
            log.error("Error in synchronous security lookup: {}", securityId, e);
            return lookupSecurityFallback(securityId, e);
        }
    }
    
    /**
     * Fallback method when circuit breaker is open or service fails.
     */
    @SuppressWarnings("unused")
    private Optional<Map<String, Object>> lookupSecurityFallback(String securityId, Exception e) {
        log.warn("Using fallback for security lookup: {}, error: {}", securityId, e.getMessage());
        // Return empty optional - enrichment service will handle partial enrichment
        return Optional.empty();
    }
}

