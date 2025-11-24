package com.pb.synth.tradecapture.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Product taxonomy information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductTaxonomy {

    @JsonProperty("primaryAssetClass")
    private String primaryAssetClass;
}

