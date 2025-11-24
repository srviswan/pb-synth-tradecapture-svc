package com.pb.synth.tradecapture.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Criterion for rule evaluation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Criterion {

    @JsonProperty("field")
    private String field;

    @JsonProperty("operator")
    private String operator; // EQUALS, NOT_EQUALS, GREATER_THAN, etc.

    @JsonProperty("value")
    private Object value;

    @JsonProperty("logicalOperator")
    private String logicalOperator; // AND, OR
}

