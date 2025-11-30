package com.pb.synth.tradecapture.service;

import com.pb.synth.tradecapture.model.Action;
import com.pb.synth.tradecapture.model.Criterion;
import com.pb.synth.tradecapture.model.Rule;
import com.pb.synth.tradecapture.model.SwapBlotter;
import com.pb.synth.tradecapture.repository.RulesRepository;
import com.pb.synth.tradecapture.service.cache.RulesCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Engine for evaluating and applying rules.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RulesEngine {

    private final RulesRepository rulesRepository;
    private final RulesCacheService rulesCacheService;

    /**
     * Apply all rules (Economic, Non-Economic, Workflow) to SwapBlotter.
     */
    public SwapBlotter applyRules(SwapBlotter swapBlotter, Map<String, Object> tradeData) {
        List<String> appliedRuleIds = new ArrayList<>();
        
        // Apply economic rules first
        List<String> economicRules = applyEconomicRules(swapBlotter, tradeData);
        appliedRuleIds.addAll(economicRules);
        
        // Apply non-economic rules second
        List<String> nonEconomicRules = applyNonEconomicRules(swapBlotter, tradeData);
        appliedRuleIds.addAll(nonEconomicRules);
        
        // Apply workflow rules last
        List<String> workflowRules = applyWorkflowRules(swapBlotter, tradeData);
        appliedRuleIds.addAll(workflowRules);
        
        // Track applied rules in metadata
        if (swapBlotter.getProcessingMetadata() != null) {
            swapBlotter.getProcessingMetadata().setRulesApplied(appliedRuleIds);
        }
        
        return swapBlotter;
    }

    /**
     * Apply economic rules.
     * Uses Redis cache (L1) first, then in-memory repository (L2) as fallback.
     */
    public List<String> applyEconomicRules(SwapBlotter swapBlotter, Map<String, Object> tradeData) {
        List<Rule> rules;
        
        // Try cache first (L1)
        Optional<List<Rule>> cachedRules = rulesCacheService.getEconomicRules();
        if (cachedRules.isPresent()) {
            log.debug("Economic rules cache hit");
            rules = cachedRules.get();
        } else {
            // Fallback to repository (L2)
            rules = rulesRepository.getEconomicRules();
            // Cache for future lookups
            if (!rules.isEmpty()) {
                rulesCacheService.putEconomicRules(rules);
            }
        }
        
        List<String> appliedRuleIds = new ArrayList<>();
        
        for (Rule rule : rules) {
            if (matchesCriteria(rule.getCriteria(), tradeData)) {
                executeActions(rule.getActions(), swapBlotter, tradeData);
                appliedRuleIds.add(rule.getId());
                log.info("Applied economic rule: {} - {}", rule.getId(), rule.getName());
            }
        }
        
        return appliedRuleIds;
    }

    /**
     * Apply non-economic rules.
     * Uses Redis cache (L1) first, then in-memory repository (L2) as fallback.
     */
    public List<String> applyNonEconomicRules(SwapBlotter swapBlotter, Map<String, Object> tradeData) {
        List<Rule> rules;
        
        // Try cache first (L1)
        Optional<List<Rule>> cachedRules = rulesCacheService.getNonEconomicRules();
        if (cachedRules.isPresent()) {
            log.debug("Non-economic rules cache hit");
            rules = cachedRules.get();
        } else {
            // Fallback to repository (L2)
            rules = rulesRepository.getNonEconomicRules();
            // Cache for future lookups
            if (!rules.isEmpty()) {
                rulesCacheService.putNonEconomicRules(rules);
            }
        }
        
        List<String> appliedRuleIds = new ArrayList<>();
        
        for (Rule rule : rules) {
            if (matchesCriteria(rule.getCriteria(), tradeData)) {
                executeActions(rule.getActions(), swapBlotter, tradeData);
                appliedRuleIds.add(rule.getId());
                log.info("Applied non-economic rule: {} - {}", rule.getId(), rule.getName());
            }
        }
        
        return appliedRuleIds;
    }

    /**
     * Apply workflow rules.
     * Uses Redis cache (L1) first, then in-memory repository (L2) as fallback.
     */
    public List<String> applyWorkflowRules(SwapBlotter swapBlotter, Map<String, Object> tradeData) {
        List<Rule> rules;
        
        // Try cache first (L1)
        Optional<List<Rule>> cachedRules = rulesCacheService.getWorkflowRules();
        if (cachedRules.isPresent()) {
            log.debug("Workflow rules cache hit");
            rules = cachedRules.get();
        } else {
            // Fallback to repository (L2)
            rules = rulesRepository.getWorkflowRules();
            // Cache for future lookups
            if (!rules.isEmpty()) {
                rulesCacheService.putWorkflowRules(rules);
            }
        }
        
        List<String> appliedRuleIds = new ArrayList<>();
        
        for (Rule rule : rules) {
            if (matchesCriteria(rule.getCriteria(), tradeData)) {
                executeActions(rule.getActions(), swapBlotter, tradeData);
                appliedRuleIds.add(rule.getId());
                log.info("Applied workflow rule: {} - {}", rule.getId(), rule.getName());
                break; // First matching rule determines workflow status
            }
        }
        
        return appliedRuleIds;
    }

    /**
     * Check if criteria match.
     */
    private boolean matchesCriteria(List<Criterion> criteria, Map<String, Object> tradeData) {
        if (criteria == null || criteria.isEmpty()) {
            return true;
        }
        
        boolean result = true;
        String logicalOperator = "AND";
        
        for (Criterion criterion : criteria) {
            boolean matches = evaluateCriterion(criterion, tradeData);
            
            if (logicalOperator.equals("AND")) {
                result = result && matches;
            } else if (logicalOperator.equals("OR")) {
                result = result || matches;
            }
            
            logicalOperator = criterion.getLogicalOperator() != null ? criterion.getLogicalOperator() : "AND";
        }
        
        return result;
    }

    /**
     * Evaluate a single criterion.
     */
    private boolean evaluateCriterion(Criterion criterion, Map<String, Object> tradeData) {
        Object fieldValue = getFieldValue(criterion.getField(), tradeData);
        String operator = criterion.getOperator();
        Object expectedValue = criterion.getValue();
        
        switch (operator) {
            case "EQUALS":
                return fieldValue != null && fieldValue.equals(expectedValue);
            case "NOT_EQUALS":
                return fieldValue == null || !fieldValue.equals(expectedValue);
            case "GREATER_THAN":
                return compareNumbers(fieldValue, expectedValue) > 0;
            case "GREATER_THAN_OR_EQUAL":
                return compareNumbers(fieldValue, expectedValue) >= 0;
            case "LESS_THAN":
                return compareNumbers(fieldValue, expectedValue) < 0;
            case "LESS_THAN_OR_EQUAL":
                return compareNumbers(fieldValue, expectedValue) <= 0;
            case "EXISTS":
                return fieldValue != null;
            case "NOT_EXISTS":
                return fieldValue == null;
            default:
                return false;
        }
    }

    private Object getFieldValue(String fieldPath, Map<String, Object> data) {
        // Simplified field path resolution
        String[] parts = fieldPath.split("\\.");
        Object current = data;
        for (String part : parts) {
            if (current instanceof Map) {
                current = ((Map<?, ?>) current).get(part);
            } else {
                return null;
            }
        }
        return current;
    }

    private int compareNumbers(Object value1, Object value2) {
        if (value1 instanceof Number && value2 instanceof Number) {
            return Double.compare(((Number) value1).doubleValue(), ((Number) value2).doubleValue());
        }
        return 0;
    }

    /**
     * Execute actions.
     */
    private void executeActions(List<Action> actions, SwapBlotter swapBlotter, Map<String, Object> tradeData) {
        if (actions == null) {
            return;
        }
        
        for (Action action : actions) {
            executeAction(action, swapBlotter, tradeData);
        }
    }

    /**
     * Execute a single action.
     */
    private void executeAction(Action action, SwapBlotter swapBlotter, Map<String, Object> tradeData) {
        String type = action.getType();
        
        switch (type) {
            case "SET_WORKFLOW_STATUS":
                if (action.getValue() instanceof String) {
                    swapBlotter.setWorkflowStatus(
                        com.pb.synth.tradecapture.model.WorkflowStatus.valueOf((String) action.getValue())
                    );
                }
                break;
            // Additional action types can be implemented here
            default:
                log.warn("Unknown action type: {}", type);
        }
    }
}

