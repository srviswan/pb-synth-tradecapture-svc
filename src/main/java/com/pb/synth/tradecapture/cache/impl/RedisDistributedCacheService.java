package com.pb.synth.tradecapture.cache.impl;

import com.pb.synth.tradecapture.cache.DistributedCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis implementation of distributed cache service.
 */
@Service
@ConditionalOnProperty(name = "cache.provider", havingValue = "redis", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class RedisDistributedCacheService implements DistributedCacheService {
    
    private final StringRedisTemplate redisTemplate;
    
    @Override
    public Optional<String> get(String key) {
        try {
            String value = redisTemplate.opsForValue().get(key);
            return Optional.ofNullable(value);
        } catch (Exception e) {
            log.warn("Error getting value from Redis cache: key={}", key, e);
            return Optional.empty();
        }
    }
    
    @Override
    public void set(String key, String value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, value, ttl);
        } catch (Exception e) {
            log.warn("Error setting value in Redis cache: key={}", key, e);
            // Non-critical, continue without caching
        }
    }
    
    @Override
    public boolean setIfAbsent(String key, String value, Duration ttl) {
        try {
            Boolean result = redisTemplate.opsForValue()
                .setIfAbsent(key, value, ttl.toSeconds(), TimeUnit.SECONDS);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.warn("Error setting value if absent in Redis cache: key={}", key, e);
            return false;
        }
    }
    
    @Override
    public boolean delete(String key) {
        try {
            Boolean deleted = redisTemplate.delete(key);
            return Boolean.TRUE.equals(deleted);
        } catch (Exception e) {
            log.warn("Error deleting key from Redis cache: key={}", key, e);
            return false;
        }
    }
    
    @Override
    public boolean exists(String key) {
        try {
            Boolean exists = redisTemplate.hasKey(key);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            log.warn("Error checking key existence in Redis cache: key={}", key, e);
            return false;
        }
    }
    
    @Override
    public boolean expire(String key, Duration ttl) {
        try {
            Boolean expired = redisTemplate.expire(key, ttl.toSeconds(), TimeUnit.SECONDS);
            return Boolean.TRUE.equals(expired);
        } catch (Exception e) {
            log.warn("Error setting expiration for key in Redis cache: key={}", key, e);
            return false;
        }
    }
    
    @Override
    public long increment(String key) {
        try {
            Long result = redisTemplate.opsForValue().increment(key);
            return result != null ? result : 0L;
        } catch (Exception e) {
            log.warn("Error incrementing value in Redis cache: key={}", key, e);
            return 0L;
        }
    }
    
    @Override
    public long incrementBy(String key, long delta) {
        try {
            Long result = redisTemplate.opsForValue().increment(key, delta);
            return result != null ? result : 0L;
        } catch (Exception e) {
            log.warn("Error incrementing value by delta in Redis cache: key={}", key, e);
            return 0L;
        }
    }
    
    @Override
    public Object executeScript(String script, Set<String> keys, String... args) {
        try {
            DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
            redisScript.setScriptText(script);
            redisScript.setResultType(Long.class);
            
            return redisTemplate.execute(redisScript, new ArrayList<>(keys), (Object[]) args);
        } catch (Exception e) {
            log.warn("Error executing Lua script in Redis: keys={}", keys, e);
            return 0L;
        }
    }
}

