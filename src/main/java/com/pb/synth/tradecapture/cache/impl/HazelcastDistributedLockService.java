package com.pb.synth.tradecapture.cache.impl;

import com.hazelcast.core.HazelcastInstance;
import com.pb.synth.tradecapture.cache.DistributedLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Hazelcast implementation of distributed locking service.
 * Uses IMap-based locking with lock value verification to ensure only the owning thread can release locks.
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
    
    // Thread-local storage for lock values to verify ownership on release
    // Key: partitionKey, Value: lockValue used when acquiring the lock
    private final ThreadLocal<Map<String, String>> threadLockValues = ThreadLocal.withInitial(ConcurrentHashMap::new);
    
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
                // Lock acquired - set TTL and store lock value for this thread
                lockMap.setTtl(lockKey, lockTimeout.toMillis(), TimeUnit.MILLISECONDS);
                // Store the lock value for this thread so we can verify ownership on release
                threadLockValues.get().put(partitionKey, lockValue);
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
        
        // Get the lock value that was used when this thread acquired the lock
        Map<String, String> threadLocks = threadLockValues.get();
        String expectedLockValue = threadLocks.get(partitionKey);
        
        if (expectedLockValue == null) {
            log.warn("Attempted to release lock for partition: {} but this thread never acquired it", partitionKey);
            return;
        }
        
        // Atomically remove the lock only if the value matches (ensures we own the lock)
        // This prevents other threads from releasing locks they don't own
        boolean removed = lockMap.remove(lockKey, expectedLockValue);
        
        if (removed) {
            // Clean up thread-local storage
            threadLocks.remove(partitionKey);
            log.debug("Released lock for partition: {}", partitionKey);
        } else {
            // Lock value doesn't match - either we don't own it or it was already released
            String currentValue = lockMap.get(lockKey);
            if (currentValue == null) {
                log.warn("Lock not found for partition: {} (may have expired or been released)", partitionKey);
            } else {
                log.warn("Lock value mismatch for partition: {} - expected: {}, found: {}. Lock not released.", 
                    partitionKey, expectedLockValue, currentValue);
            }
            // Clean up thread-local storage even if release failed
            threadLocks.remove(partitionKey);
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
        
        // Get the lock value that was used when this thread acquired the lock
        Map<String, String> threadLocks = threadLockValues.get();
        String expectedLockValue = threadLocks.get(partitionKey);
        
        if (expectedLockValue == null) {
            log.warn("Attempted to extend lock for partition: {} but this thread never acquired it", partitionKey);
            return false;
        }
        
        // Verify we own the lock before extending
        String currentValue = lockMap.get(lockKey);
        if (currentValue == null) {
            log.warn("Lock not found for partition: {} (may have expired)", partitionKey);
            threadLocks.remove(partitionKey); // Clean up
            return false;
        }
        
        if (!expectedLockValue.equals(currentValue)) {
            log.warn("Lock value mismatch for partition: {} - expected: {}, found: {}. Lock not extended.", 
                partitionKey, expectedLockValue, currentValue);
            threadLocks.remove(partitionKey); // Clean up
            return false;
        }
        
        // Extend TTL by the additional time (we own the lock)
        long currentTtl = lockMap.getEntryView(lockKey).getTtl();
        long newTtl = currentTtl + additionalTime.toMillis();
        lockMap.setTtl(lockKey, newTtl, TimeUnit.MILLISECONDS);
        log.debug("Extended lock for partition: {} by {}", partitionKey, additionalTime);
        return true;
    }
}

