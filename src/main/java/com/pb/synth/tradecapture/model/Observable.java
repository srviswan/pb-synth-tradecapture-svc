package com.pb.synth.tradecapture.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Observable asset.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Observable {

    @JsonProperty("asset")
    private Asset asset;
}

