package com.pb.synth.tradecapture.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Service for distributed locking using Redis.
 * Ensures single-threaded processing per partition across multiple service instances.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PartitionLockService {
    
    private final StringRedisTemplate redisTemplate;
    
    private static final String LOCK_PREFIX = "lock:partition:";
    private static final Duration DEFAULT_LOCK_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration DEFAULT_LOCK_WAIT = Duration.ofSeconds(30);
    
    /**
     * Acquire a distributed lock for a partition.
     * 
     * @param partitionKey The partition key
     * @return true if lock was acquired, false otherwise
     */
    public boolean acquireLock(String partitionKey) {
        return acquireLock(partitionKey, DEFAULT_LOCK_TIMEOUT, DEFAULT_LOCK_WAIT);
    }
    
    /**
     * Acquire a distributed lock for a partition with custom timeout.
     * 
     * @param partitionKey The partition key
     * @param lockTimeout How long the lock should be held
     * @param waitTimeout How long to wait for the lock
     * @return true if lock was acquired, false otherwise
     */
    public boolean acquireLock(String partitionKey, Duration lockTimeout, Duration waitTimeout) {
        String lockKey = LOCK_PREFIX + partitionKey;
        String lockValue = Thread.currentThread().getName() + "-" + System.currentTimeMillis();
        
        long startTime = System.currentTimeMillis();
        long waitMillis = waitTimeout.toMillis();
        
        while (System.currentTimeMillis() - startTime < waitMillis) {
            Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, lockValue, lockTimeout.toSeconds(), TimeUnit.SECONDS);
            
            if (Boolean.TRUE.equals(acquired)) {
                log.debug("Acquired lock for partition: {}", partitionKey);
                return true;
            }
            
            // Wait a bit before retrying
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        
        log.warn("Failed to acquire lock for partition: {} within timeout", partitionKey);
        return false;
    }
    
    /**
     * Release a distributed lock for a partition.
     * 
     * @param partitionKey The partition key
     */
    public void releaseLock(String partitionKey) {
        String lockKey = LOCK_PREFIX + partitionKey;
        Boolean deleted = redisTemplate.delete(lockKey);
        
        if (Boolean.TRUE.equals(deleted)) {
            log.debug("Released lock for partition: {}", partitionKey);
        } else {
            log.warn("Lock not found or already released for partition: {}", partitionKey);
        }
    }
    
    /**
     * Check if a lock exists for a partition.
     * 
     * @param partitionKey The partition key
     * @return true if lock exists, false otherwise
     */
    public boolean isLocked(String partitionKey) {
        String lockKey = LOCK_PREFIX + partitionKey;
        return Boolean.TRUE.equals(redisTemplate.hasKey(lockKey));
    }
    
    /**
     * Extend the lock timeout for a partition.
     * 
     * @param partitionKey The partition key
     * @param additionalTime Additional time to extend
     * @return true if lock was extended, false otherwise
     */
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

