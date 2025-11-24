package com.pb.synth.tradecapture.service;

import com.pb.synth.tradecapture.model.Action;
import com.pb.synth.tradecapture.model.Criterion;
import com.pb.synth.tradecapture.model.Rule;
import com.pb.synth.tradecapture.model.SwapBlotter;
import com.pb.synth.tradecapture.repository.RulesRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Engine for evaluating and applying rules.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RulesEngine {

    private final RulesRepository rulesRepository;

    /**
     * Apply all rules (Economic, Non-Economic, Workflow) to SwapBlotter.
     */
    public SwapBlotter applyRules(SwapBlotter swapBlotter, Map<String, Object> tradeData) {
        // Apply economic rules first
        swapBlotter = applyEconomicRules(swapBlotter, tradeData);
        
        // Apply non-economic rules second
        swapBlotter = applyNonEconomicRules(swapBlotter, tradeData);
        
        // Apply workflow rules last
        swapBlotter = applyWorkflowRules(swapBlotter, tradeData);
        
        return swapBlotter;
    }

    /**
     * Apply economic rules.
     */
    public SwapBlotter applyEconomicRules(SwapBlotter swapBlotter, Map<String, Object> tradeData) {
        List<Rule> rules = rulesRepository.getEconomicRules();
        
        for (Rule rule : rules) {
            if (matchesCriteria(rule.getCriteria(), tradeData)) {
                executeActions(rule.getActions(), swapBlotter, tradeData);
                log.debug("Applied economic rule: {}", rule.getId());
            }
        }
        
        return swapBlotter;
    }

    /**
     * Apply non-economic rules.
     */
    public SwapBlotter applyNonEconomicRules(SwapBlotter swapBlotter, Map<String, Object> tradeData) {
        List<Rule> rules = rulesRepository.getNonEconomicRules();
        
        for (Rule rule : rules) {
            if (matchesCriteria(rule.getCriteria(), tradeData)) {
                executeActions(rule.getActions(), swapBlotter, tradeData);
                log.debug("Applied non-economic rule: {}", rule.getId());
            }
        }
        
        return swapBlotter;
    }

    /**
     * Apply workflow rules.
     */
    public SwapBlotter applyWorkflowRules(SwapBlotter swapBlotter, Map<String, Object> tradeData) {
        List<Rule> rules = rulesRepository.getWorkflowRules();
        
        for (Rule rule : rules) {
            if (matchesCriteria(rule.getCriteria(), tradeData)) {
                executeActions(rule.getActions(), swapBlotter, tradeData);
                log.debug("Applied workflow rule: {}", rule.getId());
                break; // First matching rule determines workflow status
            }
        }
        
        return swapBlotter;
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

