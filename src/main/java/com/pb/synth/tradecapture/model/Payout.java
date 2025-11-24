package com.pb.synth.tradecapture.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payout union type (can be PerformancePayout, InterestPayout, etc.).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payout {

    @JsonProperty("performancePayout")
    private PerformancePayout performancePayout;

    @JsonProperty("interestRatePayout")
    private InterestRatePayout interestRatePayout;
}

