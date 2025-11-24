package com.pb.synth.tradecapture.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Quantity with value and unit.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Quantity {

    @JsonProperty("value")
    private Double value;

    @JsonProperty("unit")
    private Unit unit;
}

