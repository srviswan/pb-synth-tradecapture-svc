package com.pb.synth.tradecapture.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Return terms for performance payout.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReturnTerms {

    @JsonProperty("returnType")
    private String returnType; // Price, Total

    @JsonProperty("dividendPayoutRatio")
    private Double dividendPayoutRatio;
}

