package com.pb.synth.tradecapture.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Economic terms containing payout information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EconomicTerms {

    @JsonProperty("effectiveDate")
    private LocalDate effectiveDate;

    @JsonProperty("terminationDate")
    private LocalDate terminationDate;

    @JsonProperty("payout")
    private List<Payout> payout;
}

