package com.pb.synth.tradecapture.config;

import com.pb.synth.tradecapture.messaging.MessagingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Configuration component that initializes the messaging service.
 * The MessagingService handles provider selection and consumer lifecycle.
 * 
 * @deprecated This is now handled by MessagingService @PostConstruct.
 * Kept for backward compatibility but delegates to MessagingService.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Deprecated
public class TradeMessageConsumerConfig implements CommandLineRunner {
    
    private final MessagingService messagingService;
    
    @Override
    public void run(String... args) {
        log.info("Messaging service initialized via MessagingService");
        // MessagingService handles initialization via @PostConstruct
        // This CommandLineRunner is kept for backward compatibility
        if (messagingService.isRunning()) {
            log.info("Messaging service is running with provider: {}", 
                messagingService.getActiveConsumer() != null 
                    ? messagingService.getActiveConsumer().getClass().getSimpleName() 
                    : "unknown");
        } else {
            log.warn("Messaging service is not running");
        }
    }
}

