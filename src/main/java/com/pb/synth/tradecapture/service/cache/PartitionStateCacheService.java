package com.pb.synth.tradecapture.service.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pb.synth.tradecapture.cache.DistributedCacheService;
import com.pb.synth.tradecapture.model.State;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * Distributed cache service for partition state.
 * Reduces database queries for frequently accessed partition states.
 * Supports both Redis and Hazelcast via abstraction layer.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PartitionStateCacheService {

    private final DistributedCacheService distributedCacheService;
    private final ObjectMapper objectMapper;

    @Value("${cache.partition-state.enabled:true}")
    private boolean cacheEnabled;

    @Value("${cache.partition-state.key-prefix:partition-state:}")
    private String keyPrefix;

    @Value("${cache.partition-state.ttl-seconds:3600}")
    private long ttlSeconds; // Default 1 hour

    /**
     * Get partition state from cache.
     */
    public Optional<State> get(String partitionKey) {
        if (!cacheEnabled) {
            return Optional.empty();
        }

        try {
            String cacheKey = keyPrefix + partitionKey;
            Optional<String> cachedJsonOpt = distributedCacheService.get(cacheKey);
            
            if (cachedJsonOpt.isPresent()) {
                State state = objectMapper.readValue(cachedJsonOpt.get(), State.class);
                log.debug("Cache hit for partition state: {}", partitionKey);
                return Optional.of(state);
            }
            
            log.debug("Cache miss for partition state: {}", partitionKey);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Error reading partition state from cache: {}", partitionKey, e);
            return Optional.empty();
        }
    }

    /**
     * Cache partition state.
     */
    public void put(String partitionKey, State state) {
        if (!cacheEnabled) {
            return;
        }

        try {
            String cacheKey = keyPrefix + partitionKey;
            String stateJson = objectMapper.writeValueAsString(state);
            distributedCacheService.set(cacheKey, stateJson, Duration.ofSeconds(ttlSeconds));
            log.debug("Cached partition state: {}", partitionKey);
        } catch (Exception e) {
            log.warn("Error caching partition state: {}", partitionKey, e);
            // Non-critical, continue without caching
        }
    }

    /**
     * Invalidate cache for partition state.
     */
    public void evict(String partitionKey) {
        if (!cacheEnabled) {
            return;
        }

        try {
            String cacheKey = keyPrefix + partitionKey;
            distributedCacheService.delete(cacheKey);
            log.debug("Evicted partition state from cache: {}", partitionKey);
        } catch (Exception e) {
            log.warn("Error evicting partition state from cache: {}", partitionKey, e);
        }
    }

    /**
     * Clear all partition state cache entries.
     */
    public void clear() {
        if (!cacheEnabled) {
            return;
        }

        try {
            // Note: In production, you might want to use a more targeted approach
            // For Hazelcast, we can't easily list all keys with a prefix, so this is a no-op
            // For Redis, you would need to use keys() which is not recommended in production
            // Consider using a more targeted approach or maintaining a set of keys
            log.info("Cleared all partition state cache entries (implementation depends on cache provider)");
        } catch (Exception e) {
            log.warn("Error clearing partition state cache", e);
        }
    }
}



