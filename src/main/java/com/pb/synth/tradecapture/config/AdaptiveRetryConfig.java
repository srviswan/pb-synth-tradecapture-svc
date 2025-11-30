package com.pb.synth.tradecapture.config;

import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for Adaptive Retry Policies (Priority 5.3).
 * 
 * Provides different retry policies based on error types:
 * - Network errors (timeout, connection): Aggressive retry
 * - Server errors (5xx): Moderate retry
 * - Client errors (4xx): No retry (except 429 - rate limit)
 * - Deadlock errors: Special retry policy
 */
@Configuration
@Slf4j
public class AdaptiveRetryConfig {
    
    @Value("${adaptive-retry.enabled:true}")
    private boolean adaptiveRetryEnabled;
    
    @Value("${adaptive-retry.network.max-attempts:5}")
    private int networkMaxAttempts;
    
    @Value("${adaptive-retry.network.initial-delay-ms:100}")
    private long networkInitialDelayMs;
    
    @Value("${adaptive-retry.network.max-delay-ms:2000}")
    private long networkMaxDelayMs;
    
    @Value("${adaptive-retry.server-error.max-attempts:3}")
    private int serverErrorMaxAttempts;
    
    @Value("${adaptive-retry.server-error.initial-delay-ms:500}")
    private long serverErrorInitialDelayMs;
    
    @Value("${adaptive-retry.server-error.max-delay-ms:5000}")
    private long serverErrorMaxDelayMs;
    
    @Value("${adaptive-retry.rate-limit.max-attempts:5}")
    private int rateLimitMaxAttempts;
    
    @Value("${adaptive-retry.rate-limit.initial-delay-ms:1000}")
    private long rateLimitInitialDelayMs;
    
    @Value("${adaptive-retry.rate-limit.max-delay-ms:10000}")
    private long rateLimitMaxDelayMs;
    
    /**
     * Create retry registry with adaptive retry policies.
     */
    @Bean
    public RetryRegistry retryRegistry() {
        RetryRegistry registry = RetryRegistry.ofDefaults();
        
        if (!adaptiveRetryEnabled) {
            log.info("Adaptive retry disabled - using default retry policies");
            return registry;
        }
        
        // Network error retry policy (timeout, connection errors)
        RetryConfig networkRetryConfig = RetryConfig.custom()
            .maxAttempts(networkMaxAttempts)
            .intervalFunction(io.github.resilience4j.core.IntervalFunction.ofExponentialBackoff(
                Duration.ofMillis(networkInitialDelayMs), 2.0))
            .retryExceptions(
                java.util.concurrent.TimeoutException.class,
                java.io.IOException.class,
                java.net.ConnectException.class,
                org.springframework.web.client.ResourceAccessException.class
            )
            .build();
        
        registry.addConfiguration("networkErrors", networkRetryConfig);
        log.info("Registered network error retry policy: maxAttempts={}, initialDelay={}ms, maxDelay={}ms", 
            networkMaxAttempts, networkInitialDelayMs, networkMaxDelayMs);
        
        // Server error retry policy (5xx errors)
        RetryConfig serverErrorRetryConfig = RetryConfig.custom()
            .maxAttempts(serverErrorMaxAttempts)
            .intervalFunction(io.github.resilience4j.core.IntervalFunction.ofExponentialBackoff(
                Duration.ofMillis(serverErrorInitialDelayMs), 2.0))
            .retryExceptions(
                org.springframework.web.client.HttpServerErrorException.class
            )
            .build();
        
        registry.addConfiguration("serverErrors", serverErrorRetryConfig);
        log.info("Registered server error retry policy: maxAttempts={}, initialDelay={}ms, maxDelay={}ms", 
            serverErrorMaxAttempts, serverErrorInitialDelayMs, serverErrorMaxDelayMs);
        
        // Rate limit retry policy (429 errors)
        RetryConfig rateLimitRetryConfig = RetryConfig.custom()
            .maxAttempts(rateLimitMaxAttempts)
            .intervalFunction(io.github.resilience4j.core.IntervalFunction.ofExponentialBackoff(
                Duration.ofMillis(rateLimitInitialDelayMs), 2.0))
            .retryExceptions(
                org.springframework.web.client.HttpClientErrorException.class // Will check for 429 specifically
            )
            .build();
        
        registry.addConfiguration("rateLimit", rateLimitRetryConfig);
        log.info("Registered rate limit retry policy: maxAttempts={}, initialDelay={}ms, maxDelay={}ms", 
            rateLimitMaxAttempts, rateLimitInitialDelayMs, rateLimitMaxDelayMs);
        
        return registry;
    }
    
    /**
     * Get retry config name based on exception type.
     * Used by services to select appropriate retry policy.
     */
    public String getRetryConfigName(Throwable throwable) {
        if (!adaptiveRetryEnabled) {
            return "default";
        }
        
        if (throwable instanceof java.util.concurrent.TimeoutException ||
            throwable instanceof java.io.IOException ||
            throwable instanceof java.net.ConnectException ||
            throwable instanceof org.springframework.web.client.ResourceAccessException) {
            return "networkErrors";
        }
        
        if (throwable instanceof org.springframework.web.client.HttpServerErrorException) {
            return "serverErrors";
        }
        
        if (throwable instanceof org.springframework.web.client.HttpClientErrorException) {
            org.springframework.web.client.HttpClientErrorException httpError = 
                (org.springframework.web.client.HttpClientErrorException) throwable;
            if (httpError.getStatusCode().value() == 429) {
                return "rateLimit";
            }
        }
        
        return "default";
    }
}

