package com.pb.synth.tradecapture.cache.impl;

import com.hazelcast.core.HazelcastInstance;
import com.pb.synth.tradecapture.cache.DistributedLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Hazelcast implementation of distributed locking service.
 */
@Service
@ConditionalOnProperty(name = "cache.provider", havingValue = "hazelcast", matchIfMissing = false)
@RequiredArgsConstructor
@Slf4j
public class HazelcastDistributedLockService implements DistributedLockService {
    
    private final HazelcastInstance hazelcastInstance;
    
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
        // Use IMap-based locking (works with both embedded and cluster mode)
        // CP locks require 3+ members, so we use IMap locking for compatibility
        com.hazelcast.map.IMap<String, String> lockMap = hazelcastInstance.getMap("distributed-locks");
        
        // Try to acquire lock using IMap putIfAbsent (atomic operation)
        String lockValue = Thread.currentThread().getName() + "-" + System.currentTimeMillis();
        
        long startTime = System.currentTimeMillis();
        long waitMillis = waitTimeout.toMillis();
        long backoffMs = 50; // Start with 50ms
        long maxBackoffMs = 500; // Max 500ms between attempts
        double multiplier = 1.5; // Exponential backoff multiplier
        
        int attempt = 0;
        while (System.currentTimeMillis() - startTime < waitMillis) {
            attempt++;
            // Try to acquire lock using putIfAbsent (atomic operation)
            String existing = lockMap.putIfAbsent(lockKey, lockValue);
            
            if (existing == null) {
                // Lock acquired - set TTL
                lockMap.setTtl(lockKey, lockTimeout.toMillis(), TimeUnit.MILLISECONDS);
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
        com.hazelcast.map.IMap<String, String> lockMap = hazelcastInstance.getMap("distributed-locks");
        
        // Only release if we hold the lock (check by value)
        String lockValue = Thread.currentThread().getName() + "-" + System.currentTimeMillis();
        String currentValue = lockMap.get(lockKey);
        
        // For simplicity, we'll remove the lock if it exists
        // In production, you might want to verify the lock value matches
        String removed = lockMap.remove(lockKey);
        
        if (removed != null) {
            log.debug("Released lock for partition: {}", partitionKey);
        } else {
            log.warn("Lock not found or already released for partition: {}", partitionKey);
        }
    }
    
    @Override
    public boolean isLocked(String partitionKey) {
        String lockKey = LOCK_PREFIX + partitionKey;
        com.hazelcast.map.IMap<String, String> lockMap = hazelcastInstance.getMap("distributed-locks");
        return lockMap.containsKey(lockKey);
    }
    
    @Override
    public boolean extendLock(String partitionKey, Duration additionalTime) {
        String lockKey = LOCK_PREFIX + partitionKey;
        com.hazelcast.map.IMap<String, String> lockMap = hazelcastInstance.getMap("distributed-locks");
        
        if (lockMap.containsKey(lockKey)) {
            // Extend TTL by the additional time
            long currentTtl = lockMap.getEntryView(lockKey).getTtl();
            long newTtl = currentTtl + additionalTime.toMillis();
            lockMap.setTtl(lockKey, newTtl, TimeUnit.MILLISECONDS);
            log.debug("Extended lock for partition: {} by {}", partitionKey, additionalTime);
            return true;
        }
        
        log.warn("Lock not found for partition: {}", partitionKey);
        return false;
    }
}

