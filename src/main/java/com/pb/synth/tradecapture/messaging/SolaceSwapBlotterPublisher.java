package com.pb.synth.tradecapture.messaging;

import com.pb.synth.tradecapture.model.SwapBlotter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Solace publisher for SwapBlotter.
 * This is a placeholder implementation - actual Solace integration would be implemented here.
 */
@Component
@ConditionalOnProperty(name = "messaging.solace.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class SolaceSwapBlotterPublisher implements SwapBlotterPublisher {

    @Value("${messaging.solace.queues.output:trade/capture/blotter}")
    private String outputQueue;

    @Override
    public void publish(SwapBlotter swapBlotter) {
        log.info("Publishing SwapBlotter to Solace queue {}: {}", outputQueue, swapBlotter.getTradeId());
        // TODO: Implement actual Solace publishing
        // - Serialize SwapBlotter to protobuf
        // - Publish to Solace queue/topic
        // - Handle partition key routing
    }
}

