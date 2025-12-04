package com.pb.synth.tradecapture.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Factory for selecting and providing the appropriate TradeInputPublisher
 * based on configuration.
 * 
 * This factory abstracts the selection logic and provides a single point
 * for getting the configured publisher implementation.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TradeInputPublisherFactory {
    
    private final ApplicationContext applicationContext;
    
    @Value("${messaging.kafka.enabled:true}")
    private boolean kafkaEnabled;
    
    @Value("${messaging.solace.enabled:false}")
    private boolean solaceEnabled;
    
    /**
     * Get the configured TradeInputPublisher based on enabled messaging systems.
     * Priority: Solace > Kafka
     * 
     * @return The configured publisher, or empty if none available
     */
    public Optional<TradeInputPublisher> getPublisher() {
        // Check Solace first (if enabled)
        if (solaceEnabled) {
            TradeInputPublisher solacePublisher = getBeanSafely(
                "solaceTradeInputPublisher", 
                SolaceTradeInputPublisher.class
            );
            if (solacePublisher != null && solacePublisher.isAvailable()) {
                log.debug("Using SolaceTradeInputPublisher");
                return Optional.of(solacePublisher);
            }
        }
        
        // Fallback to Kafka (if enabled)
        if (kafkaEnabled) {
            TradeInputPublisher kafkaPublisher = getBeanSafely(
                "kafkaTradeInputPublisher",
                KafkaTradeInputPublisher.class
            );
            if (kafkaPublisher != null && kafkaPublisher.isAvailable()) {
                log.debug("Using KafkaTradeInputPublisher");
                return Optional.of(kafkaPublisher);
            }
        }
        
        log.warn("No TradeInputPublisher available. Kafka enabled: {}, Solace enabled: {}", 
            kafkaEnabled, solaceEnabled);
        return Optional.empty();
    }
    
    /**
     * Safely get a bean from the application context.
     */
    private <T> T getBeanSafely(String beanName, Class<T> beanType) {
        try {
            if (applicationContext.containsBean(beanName)) {
                return applicationContext.getBean(beanName, beanType);
            }
            return null;
        } catch (Exception e) {
            log.debug("Bean {} not available: {}", beanName, e.getMessage());
            return null;
        }
    }
}

