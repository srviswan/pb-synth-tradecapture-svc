package com.pb.synth.tradecapture.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Price with value and unit.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Price {

    @JsonProperty("value")
    private Double value;

    @JsonProperty("unit")
    private Unit unit;
}

