package com.pb.synth.tradecapture.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Performance payout representing the equity leg of an equity swap.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerformancePayout {

    @JsonProperty("payerReceiver")
    private PayerReceiver payerReceiver;

    @JsonProperty("priceQuantity")
    private PriceQuantity priceQuantity;

    @JsonProperty("underlier")
    private Underlier underlier;

    @JsonProperty("returnTerms")
    private ReturnTerms returnTerms;
}

