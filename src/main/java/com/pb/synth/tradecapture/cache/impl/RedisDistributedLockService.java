package com.pb.synth.tradecapture.cache.impl;

import com.pb.synth.tradecapture.cache.DistributedLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Redis implementation of distributed locking service.
 */
@Service
@ConditionalOnProperty(name = "cache.provider", havingValue = "redis", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class RedisDistributedLockService implements DistributedLockService {
    
    private final StringRedisTemplate redisTemplate;
    
    private static final String LOCK_PREFIX = "lock:partition:";
    private static final Duration DEFAULT_LOCK_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration DEFAULT_LOCK_WAIT = Duration.ofSeconds(30);
    
    @Override
    public boolean acquireLock(String partitionKey) {
        return acquireLock(partitionKey, DEFAULT_LOCK_TIMEOUT, DEFAULT_LOCK_WAIT);
    }
    
    @Override
    public boolean acquireLock(String partitionKey, Duration lockTimeout, Duration waitTimeout) {
        String lockKey = LOCK_PREFIX + partitionKey;
        String lockValue = Thread.currentThread().getName() + "-" + System.currentTimeMillis();
        
        long startTime = System.currentTimeMillis();
        long waitMillis = waitTimeout.toMillis();
        long backoffMs = 50; // Start with 50ms
        long maxBackoffMs = 500; // Max 500ms between attempts
        double multiplier = 1.5; // Exponential backoff multiplier
        
        int attempt = 0;
        while (System.currentTimeMillis() - startTime < waitMillis) {
            attempt++;
            Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, lockValue, lockTimeout.toSeconds(), TimeUnit.SECONDS);
            
            if (Boolean.TRUE.equals(acquired)) {
                log.debug("Acquired lock for partition: {} after {} attempts", partitionKey, attempt);
                return true;
            }
            
            // Exponential backoff - wait longer between retries
            long remainingTime = waitMillis - (System.currentTimeMillis() - startTime);
            long sleepTime = Math.min(backoffMs, remainingTime);
            
            if (sleepTime > 0) {
                try {
                    Thread.sleep(sleepTime);
                    // Increase backoff for next attempt (exponential)
                    backoffMs = Math.min((long) (backoffMs * multiplier), maxBackoffMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Interrupted while waiting for lock: {}", partitionKey);
                    return false;
                }
            } else {
                // No time remaining
                break;
            }
        }
        
        log.warn("Failed to acquire lock for partition: {} within timeout after {} attempts", 
            partitionKey, attempt);
        return false;
    }
    
    @Override
    public void releaseLock(String partitionKey) {
        String lockKey = LOCK_PREFIX + partitionKey;
        Boolean deleted = redisTemplate.delete(lockKey);
        
        if (Boolean.TRUE.equals(deleted)) {
            log.debug("Released lock for partition: {}", partitionKey);
        } else {
            log.warn("Lock not found or already released for partition: {}", partitionKey);
        }
    }
    
    @Override
    public boolean isLocked(String partitionKey) {
        String lockKey = LOCK_PREFIX + partitionKey;
        return Boolean.TRUE.equals(redisTemplate.hasKey(lockKey));
    }
    
    @Override
    public boolean extendLock(String partitionKey, Duration additionalTime) {
        String lockKey = LOCK_PREFIX + partitionKey;
        Boolean extended = redisTemplate.expire(lockKey, additionalTime.toSeconds(), TimeUnit.SECONDS);
        
        if (Boolean.TRUE.equals(extended)) {
            log.debug("Extended lock for partition: {} by {}", partitionKey, additionalTime);
            return true;
        }
        
        log.warn("Failed to extend lock for partition: {}", partitionKey);
        return false;
    }
}

