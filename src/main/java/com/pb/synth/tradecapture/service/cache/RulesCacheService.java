package com.pb.synth.tradecapture.service.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pb.synth.tradecapture.model.Rule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Redis cache service for rules.
 * Provides distributed caching for rules across service instances.
 * Cache is invalidated when rules are updated.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RulesCacheService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${cache.rules.enabled:true}")
    private boolean cacheEnabled;

    @Value("${cache.rules.key-prefix:rules:}")
    private String keyPrefix;

    @Value("${cache.rules.ttl-seconds:3600}")
    private long ttlSeconds; // Default 1 hour

    private static final String ECONOMIC_RULES_KEY = "economic";
    private static final String NON_ECONOMIC_RULES_KEY = "non-economic";
    private static final String WORKFLOW_RULES_KEY = "workflow";
    private static final String RULE_BY_ID_KEY = "by-id:";

    /**
     * Get economic rules from cache.
     */
    public Optional<List<Rule>> getEconomicRules() {
        return getRules(ECONOMIC_RULES_KEY);
    }

    /**
     * Get non-economic rules from cache.
     */
    public Optional<List<Rule>> getNonEconomicRules() {
        return getRules(NON_ECONOMIC_RULES_KEY);
    }

    /**
     * Get workflow rules from cache.
     */
    public Optional<List<Rule>> getWorkflowRules() {
        return getRules(WORKFLOW_RULES_KEY);
    }

    /**
     * Get rule by ID from cache.
     */
    public Optional<Rule> getRuleById(String ruleId) {
        if (!cacheEnabled) {
            return Optional.empty();
        }

        try {
            String cacheKey = keyPrefix + RULE_BY_ID_KEY + ruleId;
            String cachedJson = redisTemplate.opsForValue().get(cacheKey);
            
            if (cachedJson != null) {
                Rule rule = objectMapper.readValue(cachedJson, Rule.class);
                log.debug("Cache hit for rule: {}", ruleId);
                return Optional.of(rule);
            }
            
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Error reading rule from cache: {}", ruleId, e);
            return Optional.empty();
        }
    }

    /**
     * Cache economic rules.
     */
    public void putEconomicRules(List<Rule> rules) {
        putRules(ECONOMIC_RULES_KEY, rules);
    }

    /**
     * Cache non-economic rules.
     */
    public void putNonEconomicRules(List<Rule> rules) {
        putRules(NON_ECONOMIC_RULES_KEY, rules);
    }

    /**
     * Cache workflow rules.
     */
    public void putWorkflowRules(List<Rule> rules) {
        putRules(WORKFLOW_RULES_KEY, rules);
    }

    /**
     * Cache a single rule.
     */
    public void putRule(Rule rule) {
        if (!cacheEnabled) {
            return;
        }

        try {
            String cacheKey = keyPrefix + RULE_BY_ID_KEY + rule.getId();
            String ruleJson = objectMapper.writeValueAsString(rule);
            redisTemplate.opsForValue().set(cacheKey, ruleJson, Duration.ofSeconds(ttlSeconds));
            log.debug("Cached rule: {}", rule.getId());
        } catch (Exception e) {
            log.warn("Error caching rule: {}", rule.getId(), e);
        }
    }

    /**
     * Invalidate all rules cache.
     */
    public void invalidateAll() {
        if (!cacheEnabled) {
            return;
        }

        try {
            redisTemplate.delete(keyPrefix + ECONOMIC_RULES_KEY);
            redisTemplate.delete(keyPrefix + NON_ECONOMIC_RULES_KEY);
            redisTemplate.delete(keyPrefix + WORKFLOW_RULES_KEY);
            // Note: Individual rule cache entries will expire naturally
            log.info("Invalidated all rules cache");
        } catch (Exception e) {
            log.warn("Error invalidating rules cache", e);
        }
    }

    /**
     * Invalidate a specific rule.
     */
    public void invalidateRule(String ruleId) {
        if (!cacheEnabled) {
            return;
        }

        try {
            redisTemplate.delete(keyPrefix + RULE_BY_ID_KEY + ruleId);
            // Also invalidate rule lists (they may contain this rule)
            invalidateAll();
            log.debug("Invalidated rule: {}", ruleId);
        } catch (Exception e) {
            log.warn("Error invalidating rule: {}", ruleId, e);
        }
    }

    private Optional<List<Rule>> getRules(String ruleType) {
        if (!cacheEnabled) {
            return Optional.empty();
        }

        try {
            String cacheKey = keyPrefix + ruleType;
            String cachedJson = redisTemplate.opsForValue().get(cacheKey);
            
            if (cachedJson != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> rulesData = objectMapper.readValue(cachedJson, List.class);
                List<Rule> rules = new ArrayList<>();
                for (Map<String, Object> ruleData : rulesData) {
                    rules.add(objectMapper.convertValue(ruleData, Rule.class));
                }
                log.debug("Cache hit for {} rules", ruleType);
                return Optional.of(rules);
            }
            
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Error reading {} rules from cache", ruleType, e);
            return Optional.empty();
        }
    }

    private void putRules(String ruleType, List<Rule> rules) {
        if (!cacheEnabled) {
            return;
        }

        try {
            String cacheKey = keyPrefix + ruleType;
            String rulesJson = objectMapper.writeValueAsString(rules);
            redisTemplate.opsForValue().set(cacheKey, rulesJson, Duration.ofSeconds(ttlSeconds));
            log.debug("Cached {} rules", ruleType);
        } catch (Exception e) {
            log.warn("Error caching {} rules", ruleType, e);
        }
    }
}

