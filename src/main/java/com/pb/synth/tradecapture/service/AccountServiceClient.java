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

    @Value("${services.account.mock:false}")
    private boolean mockMode;

    /**
     * Lookup account and book with circuit breaker and retry.
     */
    @SuppressWarnings("unchecked")
    @CircuitBreaker(name = "accountService", fallbackMethod = "lookupAccountFallback")
    @Retry(name = "accountService")
    @TimeLimiter(name = "accountService")
    public CompletableFuture<Optional<Map<String, Object>>> lookupAccountAsync(String accountId, String bookId) {
        // Return mock data if mock mode is enabled
        if (mockMode) {
            log.info("Mock AccountService: Looking up account: {} / book: {}", accountId, bookId);
            return CompletableFuture.supplyAsync(() -> {
                Map<String, Object> mockAccount = new HashMap<>();
                mockAccount.put("accountId", accountId);
                mockAccount.put("bookId", bookId);
                mockAccount.put("accountName", "Test Account " + accountId);
                mockAccount.put("bookName", "Test Book " + bookId);
                mockAccount.put("status", "ACTIVE");
                mockAccount.put("currency", "USD");
                mockAccount.put("region", "US");
                mockAccount.put("legalEntity", "Test Entity");
                mockAccount.put("desk", "Trading Desk 1");
                mockAccount.put("trader", "Test Trader");
                log.debug("Mock AccountService: Returning mock data for account: {} / book: {}", accountId, bookId);
                return Optional.of(mockAccount);
            });
        }
        
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
            return lookupAccountFallback(accountId, bookId, e).join();
        }
    }
    
    /**
     * Fallback method when circuit breaker is open or service fails.
     * Must return CompletableFuture for async methods.
     * Must be public for Resilience4j to find it.
     */
    @SuppressWarnings("unused")
    public CompletableFuture<Optional<Map<String, Object>>> lookupAccountFallback(String accountId, String bookId, Throwable e) {
        log.warn("Using fallback for account lookup: {} / {}, error: {}", accountId, bookId, e != null ? e.getMessage() : "unknown");
        // Return empty optional - enrichment service will handle partial enrichment
        return CompletableFuture.completedFuture(Optional.empty());
    }
}

