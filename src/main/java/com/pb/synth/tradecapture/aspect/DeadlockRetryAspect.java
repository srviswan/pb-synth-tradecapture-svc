package com.pb.synth.tradecapture.aspect;

import com.pb.synth.tradecapture.config.DeadlockRetryConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;

/**
 * Aspect to automatically retry database operations that fail due to deadlocks.
 * Detects SQL Server deadlock errors (error code 1205) and retries with exponential backoff.
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
@Order(1) // Execute before transaction aspect
public class DeadlockRetryAspect {

    private final DeadlockRetryConfig deadlockRetryConfig;
    private final PlatformTransactionManager transactionManager;

    /**
     * Retry database operations that fail due to deadlocks.
     * Applies to methods annotated with @Transactional in repository and service layers.
     * Only retries on actual SQL Server deadlock exceptions (error code 1205).
     * 
     * Uses REQUIRES_NEW transaction propagation for retries to ensure each retry
     * gets a fresh transaction, avoiding rollback-only transaction issues.
     */
    @Around("@annotation(org.springframework.transaction.annotation.Transactional) && " +
            "!execution(* com.pb.synth.tradecapture.service.*.*Fallback(..))")
    public Object retryOnDeadlock(ProceedingJoinPoint joinPoint) throws Throwable {
        RetryTemplate retryTemplate = deadlockRetryConfig.createDeadlockRetryTemplate();

        try {
            // First attempt - proceed normally with existing transaction
            try {
                return joinPoint.proceed();
            } catch (Throwable firstException) {
                // Check if it's a deadlock
                if (!isDeadlockException(firstException)) {
                    // Not a deadlock, propagate immediately
                    throw firstException;
                }
                
                // Deadlock detected - log and retry with new transactions
                log.warn("Deadlock detected on first attempt for method {}. Retrying with new transactions...",
                    joinPoint.getSignature().toShortString());
                
                // Retry with new transactions (REQUIRES_NEW)
                return retryTemplate.execute(context -> {
                    int attempt = context.getRetryCount() + 2; // +2 because first attempt already happened
                    log.warn("Deadlock retry attempt {} for method {}",
                        attempt, joinPoint.getSignature().toShortString());
                    
                    // Create a new transaction for this retry attempt
                    DefaultTransactionDefinition txDef = new DefaultTransactionDefinition();
                    txDef.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                    txDef.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
                    
                    TransactionTemplate txTemplate = new TransactionTemplate(transactionManager, txDef);
                    
                    return txTemplate.execute(status -> {
                        try {
                            return joinPoint.proceed();
                        } catch (Throwable retryException) {
                            // Check if it's still a deadlock
                            if (isDeadlockException(retryException)) {
                                log.warn("Deadlock still occurring on retry attempt {} for method {}",
                                    attempt, joinPoint.getSignature().toShortString());
                                throw new RuntimeException("Deadlock on retry", retryException);
                            }
                            // Different exception - wrap and throw
                            throw new RuntimeException("Non-deadlock exception on retry", retryException);
                        }
                    });
                });
            }
        } catch (Exception e) {
            // If retry exhausted or non-retryable exception, propagate
            Throwable cause = e.getCause();
            if (cause != null) {
                // Unwrap the actual exception
                if (cause.getCause() != null) {
                    throw cause.getCause();
                }
                throw cause;
            }
            throw e;
        }
    }

    /**
     * Check if the exception is a SQL Server deadlock.
     * SQL Server deadlock error code is 1205.
     */
    private boolean isDeadlockException(Throwable throwable) {
        Throwable cause = throwable;
        int depth = 0;
        final int maxDepth = 10; // Prevent infinite loops

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
                    message.contains("Transaction (Process ID") && message.contains("was deadlocked")
                )) {
                    return true;
                }
            }
            cause = cause.getCause();
            depth++;
        }

        return false;
    }
}

