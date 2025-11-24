package com.pb.synth.tradecapture.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Represents a trade lot with identifiers and price/quantity information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeLot {

    @JsonProperty("lotIdentifier")
    private List<Identifier> lotIdentifier;

    @JsonProperty("priceQuantity")
    private List<PriceQuantity> priceQuantity;
}

