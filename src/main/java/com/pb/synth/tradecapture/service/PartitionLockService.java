package com.pb.synth.tradecapture.service;

import com.pb.synth.tradecapture.cache.DistributedLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Service for distributed locking.
 * Uses abstraction layer to support both Redis and Hazelcast.
 * Ensures single-threaded processing per partition across multiple service instances.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PartitionLockService {
    
    private final DistributedLockService distributedLockService;
    
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
     * Uses exponential backoff to reduce contention.
     * 
     * @param partitionKey The partition key
     * @param lockTimeout How long the lock should be held
     * @param waitTimeout How long to wait for the lock
     * @return true if lock was acquired, false otherwise
     */
    public boolean acquireLock(String partitionKey, Duration lockTimeout, Duration waitTimeout) {
        return distributedLockService.acquireLock(partitionKey, lockTimeout, waitTimeout);
    }
    
    /**
     * Release a distributed lock for a partition.
     * 
     * @param partitionKey The partition key
     */
    public void releaseLock(String partitionKey) {
        distributedLockService.releaseLock(partitionKey);
    }
    
    /**
     * Check if a lock exists for a partition.
     * 
     * @param partitionKey The partition key
     * @return true if lock exists, false otherwise
     */
    public boolean isLocked(String partitionKey) {
        return distributedLockService.isLocked(partitionKey);
    }
    
    /**
     * Extend the lock timeout for a partition.
     * 
     * @param partitionKey The partition key
     * @param additionalTime Additional time to extend
     * @return true if lock was extended, false otherwise
     */
    public boolean extendLock(String partitionKey, Duration additionalTime) {
        return distributedLockService.extendLock(partitionKey, additionalTime);
    }
}

