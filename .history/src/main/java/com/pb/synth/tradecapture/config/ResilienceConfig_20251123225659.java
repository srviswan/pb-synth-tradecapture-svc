package com.pb.synth.tradecapture.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Configuration for Resilience4j (circuit breakers, retries, etc.).
 */
@Configuration
public class ResilienceConfig {
    
    @Value("${services.security-master.retry.max-attempts:3}")
    private int maxRetryAttempts;
    
    @Value("${services.security-master.retry.backoff-delay:1000}")
    private long backoffDelay;
    
    @Value("${services.security-master.timeout:5000}")
    private int timeout;
    
    /**
     * Circuit breaker configuration for external services.
     */
    @Bean
    public CircuitBreakerConfig circuitBreakerConfig() {
        return CircuitBreakerConfig.custom()
            .failureRateThreshold(50) // Open circuit after 50% failure rate
            .waitDurationInOpenState(Duration.ofSeconds(30)) // Wait 30s before half-open
            .slidingWindowSize(10) // Last 10 calls
            .minimumNumberOfCalls(5) // Minimum calls before calculating failure rate
            .permittedNumberOfCallsInHalfOpenState(3) // Allow 3 calls in half-open
            .slowCallRateThreshold(50) // Consider slow if > 50% are slow
            .slowCallDurationThreshold(Duration.ofMillis(timeout * 2)) // Slow if > 2x timeout
            .build();
    }
    
    /**
     * Retry configuration with exponential backoff.
     */
    @Bean
    public RetryConfig retryConfig() {
        return RetryConfig.custom()
            .maxAttempts(maxRetryAttempts)
            .waitDuration(Duration.ofMillis(backoffDelay))
            .intervalFunction(IntervalFunction.ofExponentialBackoff(
                Duration.ofMillis(backoffDelay), 2.0))
            .retryExceptions(Exception.class) // Retry on any exception
            .build();
    }
    
    /**
     * Time limiter configuration.
     */
    @Bean
    public TimeLimiterConfig timeLimiterConfig() {
        return TimeLimiterConfig.custom()
            .timeoutDuration(Duration.ofMillis(timeout))
            .cancelRunningFuture(true) // Cancel if timeout
            .build();
    }
}

