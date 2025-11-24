package com.pb.synth.tradecapture.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payer and receiver information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayerReceiver {

    @JsonProperty("payer")
    private String payer;

    @JsonProperty("receiver")
    private String receiver;
}

