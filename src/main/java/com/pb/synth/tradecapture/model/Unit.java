package com.pb.synth.tradecapture.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Unit can be currency or financial unit (shares, contracts, etc.).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Unit {

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("financialUnit")
    private String financialUnit;
}

