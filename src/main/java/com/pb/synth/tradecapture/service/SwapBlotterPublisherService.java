package com.pb.synth.tradecapture.service;

import com.pb.synth.tradecapture.messaging.MessagingPublisherService;
import com.pb.synth.tradecapture.model.SwapBlotter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for publishing SwapBlotter to configured messaging providers.
 * 
 * This service delegates to MessagingPublisherService which handles provider selection
 * and routing to the appropriate publisher (Kafka, Solace, etc.).
 * 
 * @deprecated Use MessagingPublisherService directly for better control.
 * This service is kept for backward compatibility.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SwapBlotterPublisherService {

    private final MessagingPublisherService messagingPublisherService;

    /**
     * Publish SwapBlotter to the configured messaging provider.
     * Delegates to MessagingPublisherService for provider selection.
     */
    public void publish(SwapBlotter swapBlotter) {
        messagingPublisherService.publish(swapBlotter);
    }
}

