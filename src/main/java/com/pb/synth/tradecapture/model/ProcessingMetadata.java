package com.pb.synth.tradecapture.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * Metadata about the processing of a trade.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessingMetadata {

    @JsonProperty("processedAt")
    private ZonedDateTime processedAt;

    @JsonProperty("processingTimeMs")
    private Long processingTimeMs;

    @JsonProperty("rulesApplied")
    private List<String> rulesApplied;

    @JsonProperty("enrichmentSources")
    private List<String> enrichmentSources;
}

