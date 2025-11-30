package com.pb.synth.tradecapture.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
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

    @Value("${services.security-master.mock:false}")
    private boolean mockMode;

    /**
     * Lookup security by security ID with circuit breaker and retry.
     */
    @SuppressWarnings("unchecked")
    @CircuitBreaker(name = "securityMasterService", fallbackMethod = "lookupSecurityFallback")
    @Retry(name = "securityMasterService")
    @TimeLimiter(name = "securityMasterService")
    public CompletableFuture<Optional<Map<String, Object>>> lookupSecurityAsync(String securityId) {
        // Return mock data if mock mode is enabled
        if (mockMode) {
            log.info("Mock SecurityMasterService: Looking up security: {}", securityId);
            return CompletableFuture.supplyAsync(() -> {
                Map<String, Object> mockSecurity = new HashMap<>();
                mockSecurity.put("securityId", securityId);
                mockSecurity.put("isin", securityId);
                mockSecurity.put("cusip", securityId.length() > 9 ? securityId.substring(2, 11) : "037833100");
                mockSecurity.put("sedol", "B0YQY04");
                mockSecurity.put("ticker", "AAPL");
                mockSecurity.put("name", "Mock Security " + securityId);
                mockSecurity.put("type", "EQUITY");
                mockSecurity.put("currency", "USD");
                mockSecurity.put("exchange", "NASDAQ");
                mockSecurity.put("sector", "Technology");
                mockSecurity.put("industry", "Consumer Electronics");
                log.debug("Mock SecurityMasterService: Returning mock data for security: {}", securityId);
                return Optional.of(mockSecurity);
            });
        }
        
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
            return lookupSecurityFallback(securityId, e).join();
        }
    }
    
    /**
     * Fallback method when circuit breaker is open or service fails.
     * Must return CompletableFuture for async methods.
     * Must be public for Resilience4j to find it.
     */
    @SuppressWarnings("unused")
    public CompletableFuture<Optional<Map<String, Object>>> lookupSecurityFallback(String securityId, Throwable e) {
        log.warn("Using fallback for security lookup: {}, error: {}", securityId, e != null ? e.getMessage() : "unknown");
        // Return empty optional - enrichment service will handle partial enrichment
        return CompletableFuture.completedFuture(Optional.empty());
    }
}

