package com.pb.synth.tradecapture.repository;

import com.pb.synth.tradecapture.model.Rule;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory repository for rules caching.
 * Rules are received via API integration and cached in memory.
 */
@Repository
public class RulesRepository {

    private final Map<String, Rule> rulesCache = new ConcurrentHashMap<>();
    private volatile boolean cacheInvalidated = false;

    /**
     * Get all economic rules, sorted by priority.
     */
    public List<Rule> getEconomicRules() {
        return rulesCache.values().stream()
            .filter(rule -> "ECONOMIC".equals(rule.getRuleType()))
            .filter(rule -> Boolean.TRUE.equals(rule.getEnabled()))
            .sorted((r1, r2) -> Integer.compare(r1.getPriority(), r2.getPriority()))
            .collect(Collectors.toList());
    }

    /**
     * Get all non-economic rules, sorted by priority.
     */
    public List<Rule> getNonEconomicRules() {
        return rulesCache.values().stream()
            .filter(rule -> "NON_ECONOMIC".equals(rule.getRuleType()))
            .filter(rule -> Boolean.TRUE.equals(rule.getEnabled()))
            .sorted((r1, r2) -> Integer.compare(r1.getPriority(), r2.getPriority()))
            .collect(Collectors.toList());
    }

    /**
     * Get all workflow rules, sorted by priority.
     */
    public List<Rule> getWorkflowRules() {
        return rulesCache.values().stream()
            .filter(rule -> "WORKFLOW".equals(rule.getRuleType()))
            .filter(rule -> Boolean.TRUE.equals(rule.getEnabled()))
            .sorted((r1, r2) -> Integer.compare(r1.getPriority(), r2.getPriority()))
            .collect(Collectors.toList());
    }

    /**
     * Add or update a rule.
     */
    public void saveRule(Rule rule) {
        rulesCache.put(rule.getId(), rule);
        cacheInvalidated = false;
    }

    /**
     * Get rule by ID.
     */
    public Rule getRule(String ruleId) {
        return rulesCache.get(ruleId);
    }

    /**
     * Delete/disable a rule.
     */
    public void deleteRule(String ruleId) {
        Rule rule = rulesCache.get(ruleId);
        if (rule != null) {
            rule.setEnabled(false);
            rulesCache.put(ruleId, rule);
        }
    }

    /**
     * Invalidate cache (mark for refresh).
     */
    public void invalidateCache() {
        cacheInvalidated = true;
    }

    /**
     * Check if cache is invalidated.
     */
    public boolean isCacheInvalidated() {
        return cacheInvalidated;
    }

    /**
     * Clear all rules.
     */
    public void clear() {
        rulesCache.clear();
    }
}

