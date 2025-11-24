package com.pb.synth.tradecapture.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Identifier for lot, trade, or security.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Identifier {

    @JsonProperty("identifier")
    private String identifier;

    @JsonProperty("identifierType")
    private String identifierType;
}

