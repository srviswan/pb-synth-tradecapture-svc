package com.pb.synth.tradecapture.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * SwapBlotter - Output model containing fully enriched contract.
 * Java POJO inspired by CDM TradeState.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SwapBlotter {

    @JsonProperty("tradeId")
    private String tradeId;

    @JsonProperty("partitionKey")
    private String partitionKey;

    @JsonProperty("tradeLots")
    private List<TradeLot> tradeLots;

    @JsonProperty("contract")
    private Contract contract;

    @JsonProperty("state")
    private State state;

    @JsonProperty("enrichmentStatus")
    private EnrichmentStatus enrichmentStatus;

    @JsonProperty("workflowStatus")
    private WorkflowStatus workflowStatus;

    @JsonProperty("processingMetadata")
    private ProcessingMetadata processingMetadata;

    @JsonProperty("version")
    private Long version; // For optimistic locking
}

