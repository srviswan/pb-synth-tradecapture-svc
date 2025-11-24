package com.pb.synth.tradecapture.testutil;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builder for creating SwapBlotter test data.
 */
public class SwapBlotterBuilder {
    private String tradeId;
    private String partitionKey;
    private List<Object> tradeLots;
    private Map<String, Object> contract;
    private Map<String, Object> state;
    private String enrichmentStatus;
    private String workflowStatus;
    private Map<String, Object> processingMetadata;

    public SwapBlotterBuilder() {
        this.tradeLots = new ArrayList<>();
        this.contract = new HashMap<>();
        this.state = new HashMap<>();
        this.processingMetadata = new HashMap<>();
    }

    public SwapBlotterBuilder withTradeId(String tradeId) {
        this.tradeId = tradeId;
        return this;
    }

    public SwapBlotterBuilder withPartitionKey(String partitionKey) {
        this.partitionKey = partitionKey;
        return this;
    }

    public SwapBlotterBuilder withTradeLots(List<Object> tradeLots) {
        this.tradeLots = tradeLots;
        return this;
    }

    public SwapBlotterBuilder addTradeLot(Object tradeLot) {
        this.tradeLots.add(tradeLot);
        return this;
    }

    public SwapBlotterBuilder withContract(Map<String, Object> contract) {
        this.contract = contract;
        return this;
    }

    public SwapBlotterBuilder withState(Map<String, Object> state) {
        this.state = state;
        return this;
    }

    public SwapBlotterBuilder withEnrichmentStatus(String enrichmentStatus) {
        this.enrichmentStatus = enrichmentStatus;
        return this;
    }

    public SwapBlotterBuilder withWorkflowStatus(String workflowStatus) {
        this.workflowStatus = workflowStatus;
        return this;
    }

    public SwapBlotterBuilder withProcessingMetadata(Map<String, Object> processingMetadata) {
        this.processingMetadata = processingMetadata;
        return this;
    }

    public SwapBlotterBuilder addProcessingMetadata(String key, Object value) {
        this.processingMetadata.put(key, value);
        return this;
    }

    public SwapBlotterBuilder withDefaultApprovedTrade() {
        return this
            .withTradeId("TRADE-2024-001")
            .withPartitionKey("ACC-001_BOOK-001_US0378331005")
            .withEnrichmentStatus("COMPLETE")
            .withWorkflowStatus("APPROVED")
            .addProcessingMetadata("processedAt", ZonedDateTime.now().toString())
            .addProcessingMetadata("processingTimeMs", 150L);
    }

    public SwapBlotterBuilder withDefaultPendingApprovalTrade() {
        return this
            .withTradeId("TRADE-2024-002")
            .withPartitionKey("ACC-001_BOOK-001_US0378331005")
            .withEnrichmentStatus("COMPLETE")
            .withWorkflowStatus("PENDING_APPROVAL")
            .addProcessingMetadata("processedAt", ZonedDateTime.now().toString())
            .addProcessingMetadata("processingTimeMs", 200L);
    }

    public Map<String, Object> build() {
        Map<String, Object> blotter = new HashMap<>();
        if (tradeId != null) blotter.put("tradeId", tradeId);
        if (partitionKey != null) blotter.put("partitionKey", partitionKey);
        if (!tradeLots.isEmpty()) blotter.put("tradeLots", tradeLots);
        if (!contract.isEmpty()) blotter.put("contract", contract);
        if (!state.isEmpty()) blotter.put("state", state);
        if (enrichmentStatus != null) blotter.put("enrichmentStatus", enrichmentStatus);
        if (workflowStatus != null) blotter.put("workflowStatus", workflowStatus);
        if (!processingMetadata.isEmpty()) blotter.put("processingMetadata", processingMetadata);
        return blotter;
    }
}

