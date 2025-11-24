package com.pb.synth.tradecapture.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Security information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Security {

    @JsonProperty("identifier")
    private List<Identifier> identifier;
}

