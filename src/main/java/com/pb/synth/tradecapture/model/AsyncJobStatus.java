package com.pb.synth.tradecapture.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

/**
 * Status of an async trade processing job.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AsyncJobStatus {

    @JsonProperty("jobId")
    private String jobId;

    @JsonProperty("status")
    private JobStatus status;

    @JsonProperty("progress")
    private Integer progress; // 0-100

    @JsonProperty("message")
    private String message;

    @JsonProperty("result")
    private TradeCaptureResponse result;

    @JsonProperty("error")
    private ErrorDetail error;

    @JsonProperty("createdAt")
    private ZonedDateTime createdAt;

    @JsonProperty("updatedAt")
    private ZonedDateTime updatedAt;

    @JsonProperty("estimatedCompletionTime")
    private ZonedDateTime estimatedCompletionTime;

    /**
     * Job status enumeration.
     */
    public enum JobStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED,
        CANCELLED
    }
}


