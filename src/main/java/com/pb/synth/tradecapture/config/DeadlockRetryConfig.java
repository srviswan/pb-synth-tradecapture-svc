package com.pb.synth.tradecapture.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.retry.policy.ExceptionClassifierRetryPolicy;
import org.springframework.retry.policy.NeverRetryPolicy;
import java.sql.SQLException;

/**
 * Configuration for deadlock retry logic.
 * Automatically retries database operations that fail due to deadlocks.
 */
@Configuration
@EnableRetry
@Slf4j
public class DeadlockRetryConfig {

    @Value("${deadlock-retry.enabled:true}")
    private boolean enabled;

    @Value("${deadlock-retry.max-attempts:3}")
    private int maxAttempts;

    @Value("${deadlock-retry.initial-delay-ms:50}")
    private long initialDelayMs;

    @Value("${deadlock-retry.max-delay-ms:500}")
    private long maxDelayMs;

    @Value("${deadlock-retry.multiplier:2.0}")
    private double multiplier;

    /**
     * Create a RetryTemplate for deadlock retries.
     * Only retries on SQL Server deadlock exceptions (error code 1205).
     */
    public RetryTemplate createDeadlockRetryTemplate() {
        if (!enabled) {
            // No retries - single attempt only
            SimpleRetryPolicy noRetryPolicy = new SimpleRetryPolicy();
            noRetryPolicy.setMaxAttempts(1);
            RetryTemplate template = new RetryTemplate();
            template.setRetryPolicy(noRetryPolicy);
            return template;
        }

        // Classifier retry policy - only retry on deadlock exceptions
        ExceptionClassifierRetryPolicy classifierRetryPolicy = new ExceptionClassifierRetryPolicy();
        
        // Retry policy for deadlock exceptions
        SimpleRetryPolicy deadlockRetryPolicy = new SimpleRetryPolicy();
        deadlockRetryPolicy.setMaxAttempts(maxAttempts);
        
        // Never retry for other exceptions
        NeverRetryPolicy noRetryPolicy = new NeverRetryPolicy();
        
        // Classify exceptions: retry on SQLException with error code 1205, otherwise don't retry
        classifierRetryPolicy.setExceptionClassifier(throwable -> {
            if (isDeadlockException(throwable)) {
                return deadlockRetryPolicy;
            }
            return noRetryPolicy;
        });

        // Exponential backoff
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(initialDelayMs);
        backOffPolicy.setMaxInterval(maxDelayMs);
        backOffPolicy.setMultiplier(multiplier);

        RetryTemplate retryTemplate = new RetryTemplate();
        retryTemplate.setRetryPolicy(classifierRetryPolicy);
        retryTemplate.setBackOffPolicy(backOffPolicy);

        return retryTemplate;
    }

    /**
     * Check if the exception is a SQL Server deadlock.
     */
    private boolean isDeadlockException(Throwable throwable) {
        Throwable cause = throwable;
        int depth = 0;
        final int maxDepth = 10;

        while (cause != null && depth < maxDepth) {
            if (cause instanceof SQLException) {
                SQLException sqlException = (SQLException) cause;
                // SQL Server deadlock error code is 1205
                if (sqlException.getErrorCode() == 1205) {
                    return true;
                }
                // Also check error message for deadlock keywords
                String message = sqlException.getMessage();
                if (message != null && (
                    message.contains("deadlock") ||
                    message.contains("deadlock victim") ||
                    (message.contains("Transaction (Process ID") && message.contains("was deadlocked"))
                )) {
                    return true;
                }
            }
            cause = cause.getCause();
            depth++;
        }

        return false;
    }

    @PostConstruct
    public void logConfiguration() {
        if (enabled) {
            log.info("Deadlock retry enabled: maxAttempts={}, initialDelay={}ms, maxDelay={}ms, multiplier={}",
                maxAttempts, initialDelayMs, maxDelayMs, multiplier);
        } else {
            log.info("Deadlock retry disabled");
        }
    }
}

