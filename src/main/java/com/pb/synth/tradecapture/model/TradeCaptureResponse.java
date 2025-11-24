package com.pb.synth.tradecapture.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response model for trade capture.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeCaptureResponse {

    @JsonProperty("tradeId")
    private String tradeId;

    @JsonProperty("status")
    private String status; // SUCCESS, FAILED, PENDING

    @JsonProperty("swapBlotter")
    private SwapBlotter swapBlotter;

    @JsonProperty("error")
    private ErrorDetail error;
}

