package com.pb.synth.tradecapture.cache;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

/**
 * Abstraction for distributed cache/key-value store operations.
 * Supports Redis and Hazelcast implementations.
 */
public interface DistributedCacheService {
    
    /**
     * Get a value from cache.
     * 
     * @param key The cache key
     * @return The cached value, or empty if not found
     */
    Optional<String> get(String key);
    
    /**
     * Set a value in cache with TTL.
     * 
     * @param key The cache key
     * @param value The value to cache
     * @param ttl Time to live
     */
    void set(String key, String value, Duration ttl);
    
    /**
     * Set a value in cache if the key does not exist (atomic operation).
     * Used for distributed locking and idempotency checks.
     * 
     * @param key The cache key
     * @param value The value to set
     * @param ttl Time to live
     * @return true if the key was set (did not exist), false if key already exists
     */
    boolean setIfAbsent(String key, String value, Duration ttl);
    
    /**
     * Delete a key from cache.
     * 
     * @param key The cache key
     * @return true if the key was deleted, false if key did not exist
     */
    boolean delete(String key);
    
    /**
     * Check if a key exists in cache.
     * 
     * @param key The cache key
     * @return true if key exists, false otherwise
     */
    boolean exists(String key);
    
    /**
     * Set expiration time for an existing key.
     * 
     * @param key The cache key
     * @param ttl Time to live
     * @return true if expiration was set, false if key does not exist
     */
    boolean expire(String key, Duration ttl);
    
    /**
     * Increment a numeric value in cache (atomic operation).
     * 
     * @param key The cache key
     * @return The new value after increment
     */
    long increment(String key);
    
    /**
     * Increment a numeric value in cache by a specific amount (atomic operation).
     * 
     * @param key The cache key
     * @param delta The amount to increment by
     * @return The new value after increment
     */
    long incrementBy(String key, long delta);
    
    /**
     * Execute a Lua script atomically (for complex operations like rate limiting).
     * 
     * @param script The Lua script
     * @param keys The keys used in the script
     * @param args The arguments for the script
     * @return The result of the script execution
     */
    Object executeScript(String script, Set<String> keys, String... args);
}

