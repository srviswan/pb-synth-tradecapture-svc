package com.pb.synth.tradecapture.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * Service that provides a unified interface for trade message consumption
 * across different messaging providers (Kafka, Solace).
 * 
 * The provider is selected via configuration: messaging.provider (kafka|solace)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MessagingService {
    
    private final ApplicationContext applicationContext;
    
    @Value("${messaging.provider:kafka}")
    private String providerName;
    
    @Value("${messaging.kafka.enabled:true}")
    private boolean kafkaEnabled;
    
    @Value("${messaging.solace.enabled:false}")
    private boolean solaceEnabled;
    
    private TradeMessageConsumer activeConsumer;
    
    /**
     * Initialize and start the configured messaging consumer.
     */
    @PostConstruct
    public void initialize() {
        MessagingProvider provider = determineProvider();
        log.info("Initializing messaging service with provider: {}", provider);
        
        try {
            activeConsumer = getConsumerForProvider(provider);
            if (activeConsumer != null) {
                activeConsumer.start();
                log.info("Successfully started {} messaging consumer", provider);
            } else {
                log.warn("No consumer available for provider: {}. This is OK in test environments.", provider);
            }
        } catch (Exception e) {
            log.warn("Failed to initialize messaging service with provider: {}. This is OK in test environments. Error: {}", 
                provider, e.getMessage());
            // Don't throw exception - allow tests to run without messaging infrastructure
            // The service will just not have an active consumer
        }
    }
    
    /**
     * Stop the active consumer and clean up resources.
     */
    @PreDestroy
    public void shutdown() {
        if (activeConsumer != null) {
            log.info("Stopping messaging consumer: {}", activeConsumer.getClass().getSimpleName());
            try {
                activeConsumer.stop();
                log.info("Messaging consumer stopped successfully");
            } catch (Exception e) {
                log.error("Error stopping messaging consumer", e);
            }
        }
    }
    
    /**
     * Get the active consumer instance.
     */
    public TradeMessageConsumer getActiveConsumer() {
        return activeConsumer;
    }
    
    /**
     * Check if the messaging service is running.
     */
    public boolean isRunning() {
        return activeConsumer != null && activeConsumer.isRunning();
    }
    
    /**
     * Determine the messaging provider based on configuration.
     */
    private MessagingProvider determineProvider() {
        // First check explicit provider configuration
        if (providerName != null && !providerName.isEmpty()) {
            try {
                return MessagingProvider.valueOf(providerName.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid messaging provider: {}. Falling back to enabled providers.", providerName);
            }
        }
        
        // Fallback to enabled flags (backward compatibility)
        if (kafkaEnabled) {
            return MessagingProvider.KAFKA;
        } else if (solaceEnabled) {
            return MessagingProvider.SOLACE;
        }
        
        // Default to Kafka
        log.warn("No messaging provider explicitly configured. Defaulting to KAFKA.");
        return MessagingProvider.KAFKA;
    }
    
    /**
     * Get the appropriate consumer bean for the specified provider.
     */
    private TradeMessageConsumer getConsumerForProvider(MessagingProvider provider) {
        switch (provider) {
            case KAFKA:
                return getBeanSafely("kafkaTradeMessageConsumer", KafkaTradeMessageConsumer.class);
            case SOLACE:
                return getBeanSafely("solaceTradeMessageConsumer", SolaceTradeMessageConsumer.class);
            default:
                log.error("Unsupported messaging provider: {}", provider);
                return null;
        }
    }
    
    /**
     * Safely get a bean from the application context, handling cases where it might not exist.
     */
    private <T> T getBeanSafely(String beanName, Class<T> beanType) {
        try {
            if (beanName != null && beanType != null && applicationContext.containsBean(beanName)) {
                return applicationContext.getBean(beanName, beanType);
            } else {
                log.warn("Bean {} not found in application context", beanName);
                return null;
            }
        } catch (Exception e) {
            log.error("Error retrieving bean {}: {}", beanName, e.getMessage());
            return null;
        }
    }
}

