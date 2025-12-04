package com.pb.synth.tradecapture.cache;

import java.time.Duration;

/**
 * Abstraction for distributed locking service.
 * Supports Redis and Hazelcast implementations.
 */
public interface DistributedLockService {
    
    /**
     * Acquire a distributed lock for a partition.
     * 
     * @param partitionKey The partition key
     * @return true if lock was acquired, false otherwise
     */
    boolean acquireLock(String partitionKey);
    
    /**
     * Acquire a distributed lock for a partition with custom timeout.
     * 
     * @param partitionKey The partition key
     * @param lockTimeout How long the lock should be held
     * @param waitTimeout How long to wait for the lock
     * @return true if lock was acquired, false otherwise
     */
    boolean acquireLock(String partitionKey, Duration lockTimeout, Duration waitTimeout);
    
    /**
     * Release a distributed lock for a partition.
     * 
     * @param partitionKey The partition key
     */
    void releaseLock(String partitionKey);
    
    /**
     * Check if a lock exists for a partition.
     * 
     * @param partitionKey The partition key
     * @return true if lock exists, false otherwise
     */
    boolean isLocked(String partitionKey);
    
    /**
     * Extend the lock timeout for a partition.
     * 
     * @param partitionKey The partition key
     * @param additionalTime Additional time to extend
     * @return true if lock was extended, false otherwise
     */
    boolean extendLock(String partitionKey, Duration additionalTime);
}

