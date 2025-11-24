package com.pb.synth.tradecapture.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Floating rate specification.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FloatingRateSpec {

    @JsonProperty("index")
    private String index;

    @JsonProperty("spread")
    private BigDecimal spread;
}

