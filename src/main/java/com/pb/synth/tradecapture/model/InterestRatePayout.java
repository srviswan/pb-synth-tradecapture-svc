package com.pb.synth.tradecapture.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Interest rate payout representing the interest/funding leg of an equity swap.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterestRatePayout {

    @JsonProperty("payerReceiver")
    private PayerReceiver payerReceiver;

    @JsonProperty("priceQuantity")
    private PriceQuantity priceQuantity;

    @JsonProperty("fixedRate")
    private BigDecimal fixedRate;

    @JsonProperty("floatingRate")
    private FloatingRateSpec floatingRate;

    @JsonProperty("dayCountFraction")
    private String dayCountFraction;
}

