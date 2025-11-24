package com.pb.synth.tradecapture.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Rule model for Economic, Non-Economic, and Workflow rules.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Rule {

    @JsonProperty("id")
    private String id;

    @JsonProperty("name")
    private String name;

    @JsonProperty("description")
    private String description;

    @JsonProperty("ruleType")
    private String ruleType; // ECONOMIC, NON_ECONOMIC, WORKFLOW

    @JsonProperty("target")
    private String target;

    @JsonProperty("priority")
    private Integer priority;

    @JsonProperty("enabled")
    private Boolean enabled;

    @JsonProperty("criteria")
    private List<Criterion> criteria;

    @JsonProperty("actions")
    private List<Action> actions;
}

