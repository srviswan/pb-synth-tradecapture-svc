package com.pb.synth.tradecapture.service.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pb.synth.tradecapture.cache.DistributedCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Distributed cache service for reference data (security, account).
 * Reduces external service calls for frequently accessed reference data.
 * Supports both Redis and Hazelcast via abstraction layer.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReferenceDataCacheService {

    private final DistributedCacheService distributedCacheService;
    private final ObjectMapper objectMapper;

    @Value("${cache.reference-data.enabled:true}")
    private boolean cacheEnabled;

    @Value("${cache.reference-data.security.key-prefix:ref:security:}")
    private String securityKeyPrefix;

    @Value("${cache.reference-data.account.key-prefix:ref:account:}")
    private String accountKeyPrefix;

    @Value("${cache.reference-data.security.ttl-seconds:7200}")
    private long securityTtlSeconds; // Default 2 hours

    @Value("${cache.reference-data.account.ttl-seconds:7200}")
    private long accountTtlSeconds; // Default 2 hours

    /**
     * Get security data from cache.
     */
    public Optional<Map<String, Object>> getSecurity(String securityId) {
        if (!cacheEnabled) {
            return Optional.empty();
        }

        try {
            String cacheKey = securityKeyPrefix + securityId;
            Optional<String> cachedJsonOpt = distributedCacheService.get(cacheKey);
            
            if (cachedJsonOpt.isPresent()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> securityData = objectMapper.readValue(cachedJsonOpt.get(), Map.class);
                log.debug("Cache hit for security: {}", securityId);
                return Optional.of(securityData);
            }
            
            log.debug("Cache miss for security: {}", securityId);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Error reading security from cache: {}", securityId, e);
            return Optional.empty();
        }
    }

    /**
     * Cache security data.
     */
    public void putSecurity(String securityId, Map<String, Object> securityData) {
        if (!cacheEnabled) {
            return;
        }

        try {
            String cacheKey = securityKeyPrefix + securityId;
            String securityJson = objectMapper.writeValueAsString(securityData);
            distributedCacheService.set(cacheKey, securityJson, Duration.ofSeconds(securityTtlSeconds));
            log.debug("Cached security: {}", securityId);
        } catch (Exception e) {
            log.warn("Error caching security: {}", securityId, e);
            // Non-critical, continue without caching
        }
    }

    /**
     * Get account data from cache.
     */
    public Optional<Map<String, Object>> getAccount(String accountId, String bookId) {
        if (!cacheEnabled) {
            return Optional.empty();
        }

        try {
            String cacheKey = accountKeyPrefix + accountId + ":" + bookId;
            Optional<String> cachedJsonOpt = distributedCacheService.get(cacheKey);
            
            if (cachedJsonOpt.isPresent()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> accountData = objectMapper.readValue(cachedJsonOpt.get(), Map.class);
                log.debug("Cache hit for account: {} / {}", accountId, bookId);
                return Optional.of(accountData);
            }
            
            log.debug("Cache miss for account: {} / {}", accountId, bookId);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Error reading account from cache: {} / {}", accountId, bookId, e);
            return Optional.empty();
        }
    }

    /**
     * Cache account data.
     */
    public void putAccount(String accountId, String bookId, Map<String, Object> accountData) {
        if (!cacheEnabled) {
            return;
        }

        try {
            String cacheKey = accountKeyPrefix + accountId + ":" + bookId;
            String accountJson = objectMapper.writeValueAsString(accountData);
            distributedCacheService.set(cacheKey, accountJson, Duration.ofSeconds(accountTtlSeconds));
            log.debug("Cached account: {} / {}", accountId, bookId);
        } catch (Exception e) {
            log.warn("Error caching account: {} / {}", accountId, bookId, e);
            // Non-critical, continue without caching
        }
    }

    /**
     * Invalidate security cache.
     */
    public void evictSecurity(String securityId) {
        if (!cacheEnabled) {
            return;
        }

        try {
            String cacheKey = securityKeyPrefix + securityId;
            distributedCacheService.delete(cacheKey);
            log.debug("Evicted security from cache: {}", securityId);
        } catch (Exception e) {
            log.warn("Error evicting security from cache: {}", securityId, e);
        }
    }

    /**
     * Invalidate account cache.
     */
    public void evictAccount(String accountId, String bookId) {
        if (!cacheEnabled) {
            return;
        }

        try {
            String cacheKey = accountKeyPrefix + accountId + ":" + bookId;
            distributedCacheService.delete(cacheKey);
            log.debug("Evicted account from cache: {} / {}", accountId, bookId);
        } catch (Exception e) {
            log.warn("Error evicting account from cache: {} / {}", accountId, bookId, e);
        }
    }
}



