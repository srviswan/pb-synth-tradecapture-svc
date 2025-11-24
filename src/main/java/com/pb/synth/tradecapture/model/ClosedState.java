package com.pb.synth.tradecapture.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Closed state information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClosedState {

    @JsonProperty("reason")
    private String reason;

    @JsonProperty("closureDate")
    private LocalDate closureDate;
}

