package com.pb.synth.tradecapture.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Asset information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Asset {

    @JsonProperty("security")
    private Security security;
}

