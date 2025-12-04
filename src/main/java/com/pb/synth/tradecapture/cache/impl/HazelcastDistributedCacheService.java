package com.pb.synth.tradecapture.cache.impl;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.pb.synth.tradecapture.cache.DistributedCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Hazelcast implementation of distributed cache service.
 */
@Service
@ConditionalOnProperty(name = "cache.provider", havingValue = "hazelcast", matchIfMissing = false)
@RequiredArgsConstructor
@Slf4j
public class HazelcastDistributedCacheService implements DistributedCacheService {
    
    private final HazelcastInstance hazelcastInstance;
    
    private static final String CACHE_MAP_NAME = "distributed-cache";
    
    private IMap<String, String> getCacheMap() {
        return hazelcastInstance.getMap(CACHE_MAP_NAME);
    }
    
    @Override
    public Optional<String> get(String key) {
        try {
            IMap<String, String> cache = getCacheMap();
            String value = cache.get(key);
            return Optional.ofNullable(value);
        } catch (Exception e) {
            log.warn("Error getting value from Hazelcast cache: key={}", key, e);
            return Optional.empty();
        }
    }
    
    @Override
    public void set(String key, String value, Duration ttl) {
        try {
            IMap<String, String> cache = getCacheMap();
            if (ttl.toMillis() > 0) {
                cache.put(key, value, ttl.toMillis(), TimeUnit.MILLISECONDS);
            } else {
                cache.put(key, value);
            }
        } catch (Exception e) {
            log.warn("Error setting value in Hazelcast cache: key={}", key, e);
            // Non-critical, continue without caching
        }
    }
    
    @Override
    public boolean setIfAbsent(String key, String value, Duration ttl) {
        try {
            IMap<String, String> cache = getCacheMap();
            String existing = cache.putIfAbsent(key, value);
            if (existing == null) {
                // Key was set, now set TTL if specified
                if (ttl.toMillis() > 0) {
                    getCacheMap().setTtl(key, ttl.toMillis(), TimeUnit.MILLISECONDS);
                }
                return true;
            }
            return false;
        } catch (Exception e) {
            log.warn("Error setting value if absent in Hazelcast cache: key={}", key, e);
            return false;
        }
    }
    
    @Override
    public boolean delete(String key) {
        try {
            IMap<String, String> cache = getCacheMap();
            String removed = cache.remove(key);
            return removed != null;
        } catch (Exception e) {
            log.warn("Error deleting key from Hazelcast cache: key={}", key, e);
            return false;
        }
    }
    
    @Override
    public boolean exists(String key) {
        try {
            IMap<String, String> cache = getCacheMap();
            return cache.containsKey(key);
        } catch (Exception e) {
            log.warn("Error checking key existence in Hazelcast cache: key={}", key, e);
            return false;
        }
    }
    
    @Override
    public boolean expire(String key, Duration ttl) {
        try {
            IMap<String, String> cache = getCacheMap();
            if (cache.containsKey(key)) {
                cache.setTtl(key, ttl.toMillis(), TimeUnit.MILLISECONDS);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.warn("Error setting expiration for key in Hazelcast cache: key={}", key, e);
            return false;
        }
    }
    
    @Override
    public long increment(String key) {
        try {
            IMap<String, String> cache = getCacheMap();
            // Hazelcast doesn't have atomic increment for String values
            // We need to use IAtomicLong instead
            return hazelcastInstance.getCPSubsystem()
                .getAtomicLong(key)
                .incrementAndGet();
        } catch (Exception e) {
            log.warn("Error incrementing value in Hazelcast cache: key={}", key, e);
            return 0L;
        }
    }
    
    @Override
    public long incrementBy(String key, long delta) {
        try {
            return hazelcastInstance.getCPSubsystem()
                .getAtomicLong(key)
                .addAndGet(delta);
        } catch (Exception e) {
            log.warn("Error incrementing value by delta in Hazelcast cache: key={}", key, e);
            return 0L;
        }
    }
    
    @Override
    public Object executeScript(String script, Set<String> keys, String... args) {
        // Hazelcast doesn't support Lua scripts like Redis
        // For rate limiting, we'll need to implement the token bucket algorithm
        // using Hazelcast's atomic operations
        // This is a simplified implementation - for production, you might want
        // to use Hazelcast's EntryProcessor for more complex operations
        log.warn("Lua script execution not supported in Hazelcast. Using fallback implementation.");
        
        // For rate limiting, we can parse the script and execute equivalent operations
        // This is a basic implementation - you may need to enhance this based on your needs
        if (keys.size() >= 2 && args.length >= 4) {
            String tokensKey = keys.stream().filter(k -> k.contains(":tokens")).findFirst().orElse(null);
            if (tokensKey != null) {
                // Simplified token bucket check
                long currentTokens = get(tokensKey).map(Long::parseLong).orElse(0L);
                // Parse args for token bucket algorithm
                // args[1] = requestsPerSecond, args[2] = burstSize (used for refill logic)
                // For now, we do a simple check - in production, implement full token bucket
                
                if (currentTokens >= 1) {
                    // Consume token
                    long newTokens = currentTokens - 1;
                    set(tokensKey, String.valueOf(newTokens), Duration.ofMinutes(5));
                    return 1L;
                } else {
                    return 0L;
                }
            }
        }
        
        return 0L;
    }
}

