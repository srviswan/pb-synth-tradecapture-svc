package com.pb.synth.tradecapture.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Represents price and quantity information for a trade lot.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceQuantity {

    @JsonProperty("quantity")
    private List<Quantity> quantity;

    @JsonProperty("price")
    private List<Price> price;
}

