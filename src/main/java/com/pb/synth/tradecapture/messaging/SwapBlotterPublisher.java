package com.pb.synth.tradecapture.messaging;

import com.pb.synth.tradecapture.model.SwapBlotter;

/**
 * Interface for publishing SwapBlotter to various destinations.
 */
public interface SwapBlotterPublisher {
    void publish(SwapBlotter swapBlotter);
}

