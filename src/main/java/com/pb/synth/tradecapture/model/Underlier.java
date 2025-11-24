package com.pb.synth.tradecapture.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Underlier information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Underlier {

    @JsonProperty("observable")
    private Observable observable;
}

