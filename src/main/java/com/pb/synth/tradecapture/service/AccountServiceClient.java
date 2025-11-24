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
 * Client for AccountService with circuit breaker and retry.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountServiceClient {

    private final RestTemplate restTemplate;

    @Value("${services.account.url}")
    private String baseUrl;

    @Value("${services.account.timeout:5000}")
    private int timeout;

    /**
     * Lookup account and book with circuit breaker and retry.
     */
    @SuppressWarnings("unchecked")
    @CircuitBreaker(name = "accountService", fallbackMethod = "lookupAccountFallback")
    @Retry(name = "accountService")
    @TimeLimiter(name = "accountService")
    public CompletableFuture<Optional<Map<String, Object>>> lookupAccountAsync(String accountId, String bookId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = baseUrl + "/api/v1/accounts/" + accountId + "/books/" + bookId;
                Map<String, Object> response = restTemplate.getForObject(url, Map.class);
                log.debug("Successfully looked up account: {} / {}", accountId, bookId);
                return Optional.ofNullable(response);
            } catch (Exception e) {
                log.error("Error looking up account: {} / {}", accountId, bookId, e);
                throw new RuntimeException("Failed to lookup account", e);
            }
        });
    }
    
    /**
     * Synchronous version for backward compatibility.
     */
    public Optional<Map<String, Object>> lookupAccount(String accountId, String bookId) {
        try {
            return lookupAccountAsync(accountId, bookId).get();
        } catch (Exception e) {
            log.error("Error in synchronous account lookup: {} / {}", accountId, bookId, e);
            return lookupAccountFallback(accountId, bookId, e);
        }
    }
    
    /**
     * Fallback method when circuit breaker is open or service fails.
     */
    @SuppressWarnings("unused")
    private Optional<Map<String, Object>> lookupAccountFallback(String accountId, String bookId, Exception e) {
        log.warn("Using fallback for account lookup: {} / {}, error: {}", accountId, bookId, e.getMessage());
        // Return empty optional - enrichment service will handle partial enrichment
        return Optional.empty();
    }
}

