package com.pb.synth.tradecapture.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Unit can be currency or financial unit (shares, contracts, etc.).
 * 
 * Supports both string and object formats:
 * - String: "SHARES", "USD" (for convenience)
 * - Object: {"currency": "USD"} or {"financialUnit": "Shares"}
 * 
 * Note: Custom deserializer is registered in ObjectMapperConfig
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

