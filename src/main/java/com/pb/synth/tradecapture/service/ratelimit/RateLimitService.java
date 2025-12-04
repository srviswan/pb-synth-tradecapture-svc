package com.pb.synth.tradecapture.service.ratelimit;

import com.pb.synth.tradecapture.cache.DistributedCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Service for distributed rate limiting.
 * Implements Priority 5.1: Rate Limiting
 * 
 * Uses token bucket algorithm with distributed cache (Redis or Hazelcast) for distributed rate limiting.
 * Supports both Redis and Hazelcast via abstraction layer.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimitService {
    
    private final DistributedCacheService distributedCacheService;
    
    @Value("${rate-limit.enabled:true}")
    private boolean rateLimitEnabled;
    
    @Value("${rate-limit.global.enabled:true}")
    private boolean globalRateLimitEnabled;
    
    @Value("${rate-limit.global.requests-per-second:100}")
    private int globalRequestsPerSecond;
    
    @Value("${rate-limit.global.burst-size:200}")
    private int globalBurstSize;
    
    @Value("${rate-limit.per-partition.enabled:true}")
    private boolean perPartitionRateLimitEnabled;
    
    @Value("${rate-limit.per-partition.requests-per-second:10}")
    private int perPartitionRequestsPerSecond;
    
    @Value("${rate-limit.per-partition.burst-size:20}")
    private int perPartitionBurstSize;
    
    private static final String GLOBAL_RATE_LIMIT_KEY = "rate-limit:global";
    private static final String PARTITION_RATE_LIMIT_KEY_PREFIX = "rate-limit:partition:";
    
    /**
     * Check if request is allowed (global rate limit).
     * 
     * @return true if allowed, false if rate limited
     */
    public boolean isAllowed() {
        if (!rateLimitEnabled || !globalRateLimitEnabled) {
            return true;
        }
        
        return checkRateLimit(GLOBAL_RATE_LIMIT_KEY, globalRequestsPerSecond, globalBurstSize);
    }
    
    /**
     * Check if request is allowed for a specific partition.
     * 
     * @param partitionKey The partition key
     * @return true if allowed, false if rate limited
     */
    public boolean isAllowed(String partitionKey) {
        if (!rateLimitEnabled) {
            return true;
        }
        
        // Check global rate limit first
        if (globalRateLimitEnabled && !isAllowed()) {
            log.debug("Global rate limit exceeded");
            return false;
        }
        
        // Check per-partition rate limit
        if (perPartitionRateLimitEnabled) {
            String key = PARTITION_RATE_LIMIT_KEY_PREFIX + partitionKey;
            return checkRateLimit(key, perPartitionRequestsPerSecond, perPartitionBurstSize);
        }
        
        return true;
    }
    
    /**
     * Check rate limit using token bucket algorithm with atomic operations.
     * Uses distributed cache Lua script (Redis) or atomic operations (Hazelcast) for atomic token consumption.
     * 
     * @param key Cache key for rate limit tracking
     * @param requestsPerSecond Rate limit (requests per second)
     * @param burstSize Maximum burst size
     * @return true if allowed, false if rate limited
     */
    private boolean checkRateLimit(String key, int requestsPerSecond, int burstSize) {
        try {
            // Use Lua script for atomic token bucket operations (Redis)
            // For Hazelcast, the executeScript method will use a fallback implementation
            String luaScript = 
                "local tokensKey = KEYS[1] " +
                "local lastRefillKey = KEYS[2] " +
                "local now = tonumber(ARGV[1]) " +
                "local requestsPerSecond = tonumber(ARGV[2]) " +
                "local burstSize = tonumber(ARGV[3]) " +
                "local ttl = tonumber(ARGV[4]) " +
                " " +
                "local lastRefill = tonumber(redis.call('get', lastRefillKey) or now) " +
                "local elapsed = now - lastRefill " +
                " " +
                "local tokensStr = redis.call('get', tokensKey) " +
                "local currentTokens = tonumber(tokensStr or burstSize) " +
                " " +
                "local tokensToAdd = math.floor((elapsed * requestsPerSecond) / 1000) " +
                "local newTokens = math.min(currentTokens + tokensToAdd, burstSize) " +
                " " +
                "if newTokens >= 1 then " +
                "  newTokens = newTokens - 1 " +
                "  redis.call('setex', tokensKey, ttl, tostring(newTokens)) " +
                "  redis.call('setex', lastRefillKey, ttl, tostring(now)) " +
                "  return 1 " +
                "else " +
                "  redis.call('setex', lastRefillKey, ttl, tostring(now)) " +
                "  return 0 " +
                "end";
            
            String tokensKey = key + ":tokens";
            String lastRefillKey = key + ":last-refill";
            long now = System.currentTimeMillis();
            long ttl = Duration.ofMinutes(5).toSeconds();
            
            // Execute Lua script atomically (works for Redis, fallback for Hazelcast)
            Set<String> keys = new HashSet<>();
            keys.add(tokensKey);
            keys.add(lastRefillKey);
            
            Object result = distributedCacheService.executeScript(
                luaScript,
                keys,
                String.valueOf(now),
                String.valueOf(requestsPerSecond),
                String.valueOf(burstSize),
                String.valueOf(ttl)
            );
            
            boolean allowed = result != null && (result instanceof Long && (Long) result == 1L || 
                                                  result instanceof Number && ((Number) result).longValue() == 1L);
            
            if (!allowed) {
                log.debug("Rate limit exceeded for key: {}", key);
            }
            
            return allowed;
            
        } catch (Exception e) {
            log.error("Error checking rate limit for key: {}", key, e);
            // On error, allow the request (fail open)
            return true;
        }
    }
    
    /**
     * Get current rate limit status for monitoring.
     */
    public RateLimitStatus getStatus(String partitionKey) {
        if (!rateLimitEnabled) {
            return RateLimitStatus.builder()
                .enabled(false)
                .build();
        }
        
        try {
            String tokensKey = PARTITION_RATE_LIMIT_KEY_PREFIX + partitionKey + ":tokens";
            Optional<String> tokensOpt = distributedCacheService.get(tokensKey);
            long availableTokens = tokensOpt.map(Long::parseLong).orElse((long) perPartitionBurstSize);
            
            return RateLimitStatus.builder()
                .enabled(true)
                .globalEnabled(globalRateLimitEnabled)
                .perPartitionEnabled(perPartitionRateLimitEnabled)
                .availableTokens(availableTokens)
                .maxTokens((long) perPartitionBurstSize)
                .requestsPerSecond(perPartitionRequestsPerSecond)
                .build();
        } catch (Exception e) {
            log.error("Error getting rate limit status", e);
            return RateLimitStatus.builder()
                .enabled(true)
                .error("Failed to get status: " + e.getMessage())
                .build();
        }
    }
    
    @lombok.Data
    @lombok.Builder
    public static class RateLimitStatus {
        private boolean enabled;
        private boolean globalEnabled;
        private boolean perPartitionEnabled;
        private Long availableTokens;
        private Long maxTokens;
        private Integer requestsPerSecond;
        private String error;
    }
}

