package com.pb.synth.tradecapture.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * State with PositionStatusEnum for tracking trade lifecycle state.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class State {

    @JsonProperty("positionState")
    private PositionStatusEnum positionState;

    @JsonProperty("closedState")
    private ClosedState closedState;
}

