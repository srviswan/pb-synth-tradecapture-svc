package com.pb.synth.tradecapture.controller;

import com.pb.synth.tradecapture.model.Rule;
import com.pb.synth.tradecapture.repository.RulesRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for rule management.
 */
@RestController
@RequestMapping("/api/v1/rules")
@RequiredArgsConstructor
@Slf4j
public class RulesController {

    private final RulesRepository rulesRepository;

    /**
     * Add or update economic rule.
     */
    @PostMapping("/economic")
    public ResponseEntity<Rule> createEconomicRule(@RequestBody Rule rule) {
        rule.setRuleType("ECONOMIC");
        rulesRepository.saveRule(rule);
        return ResponseEntity.status(HttpStatus.CREATED).body(rule);
    }

    /**
     * Add or update non-economic rule.
     */
    @PostMapping("/non-economic")
    public ResponseEntity<Rule> createNonEconomicRule(@RequestBody Rule rule) {
        rule.setRuleType("NON_ECONOMIC");
        rulesRepository.saveRule(rule);
        return ResponseEntity.status(HttpStatus.CREATED).body(rule);
    }

    /**
     * Add or update workflow rule.
     */
    @PostMapping("/workflow")
    public ResponseEntity<Rule> createWorkflowRule(@RequestBody Rule rule) {
        rule.setRuleType("WORKFLOW");
        rulesRepository.saveRule(rule);
        return ResponseEntity.status(HttpStatus.CREATED).body(rule);
    }

    /**
     * List all active rules.
     */
    @GetMapping
    public ResponseEntity<RulesResponse> listRules(
            @RequestParam(required = false) String ruleType) {
        
        List<Rule> economicRules = rulesRepository.getEconomicRules();
        List<Rule> nonEconomicRules = rulesRepository.getNonEconomicRules();
        List<Rule> workflowRules = rulesRepository.getWorkflowRules();
        
        return ResponseEntity.ok(new RulesResponse(economicRules, nonEconomicRules, workflowRules));
    }

    /**
     * Get specific rule.
     */
    @GetMapping("/{ruleId}")
    public ResponseEntity<Rule> getRule(@PathVariable String ruleId) {
        Rule rule = rulesRepository.getRule(ruleId);
        if (rule == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(rule);
    }

    /**
     * Delete/disable rule.
     */
    @DeleteMapping("/{ruleId}")
    public ResponseEntity<Void> deleteRule(@PathVariable String ruleId) {
        rulesRepository.deleteRule(ruleId);
        return ResponseEntity.noContent().build();
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class RulesResponse {
        private List<Rule> economic;
        private List<Rule> nonEconomic;
        private List<Rule> workflow;
    }
}

