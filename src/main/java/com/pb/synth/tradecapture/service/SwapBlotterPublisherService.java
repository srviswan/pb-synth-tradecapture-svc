package com.pb.synth.tradecapture.service;

import com.pb.synth.tradecapture.messaging.SwapBlotterPublisher;
import com.pb.synth.tradecapture.model.SwapBlotter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for publishing SwapBlotter to multiple subscribers.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SwapBlotterPublisherService {

    private final List<SwapBlotterPublisher> publishers;

    /**
     * Publish SwapBlotter to all configured subscribers.
     */
    public void publish(SwapBlotter swapBlotter) {
        for (SwapBlotterPublisher publisher : publishers) {
            try {
                publisher.publish(swapBlotter);
            } catch (Exception e) {
                log.error("Error publishing SwapBlotter via {}: {}", publisher.getClass().getSimpleName(), e.getMessage());
                // Continue with other publishers even if one fails
            }
        }
    }
}

