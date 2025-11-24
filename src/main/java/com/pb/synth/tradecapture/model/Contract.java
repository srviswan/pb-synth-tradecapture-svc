package com.pb.synth.tradecapture.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Contract wrapper around CDM NonTransferableProduct.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Contract {

    @JsonProperty("identifier")
    private List<Identifier> identifier;

    @JsonProperty("taxonomy")
    private List<ProductTaxonomy> taxonomy;

    @JsonProperty("economicTerms")
    private EconomicTerms economicTerms;
}

