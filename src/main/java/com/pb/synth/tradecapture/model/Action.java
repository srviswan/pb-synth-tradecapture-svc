package com.pb.synth.tradecapture.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Action to execute when rule criteria are met.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Action {

    @JsonProperty("type")
    private String type; // SET_FIELD, SET_DAY_COUNT, SET_WORKFLOW_STATUS, etc.

    @JsonProperty("target")
    private String target;

    @JsonProperty("value")
    private Object value;

    @JsonProperty("parameters")
    private Map<String, Object> parameters;
}

