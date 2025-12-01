package com.pb.synth.tradecapture.messaging;

import com.pb.synth.tradecapture.model.SwapBlotter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Service that provides a unified interface for publishing SwapBlotter messages
 * across different messaging providers (Kafka, Solace).
 * 
 * The provider is selected via configuration: messaging.provider (kafka|solace)
 * Additional publishers (e.g., webhooks) can be enabled alongside the primary provider.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MessagingPublisherService {
    
    private final ApplicationContext applicationContext;
    
    @Value("${messaging.provider:kafka}")
    private String providerName;
    
    @Value("${messaging.kafka.enabled:true}")
    private boolean kafkaEnabled;
    
    @Value("${messaging.solace.enabled:false}")
    private boolean solaceEnabled;
    
    @Value("${publishing.webhook.enabled:false}")
    private boolean webhookEnabled;
    
    private SwapBlotterPublisher primaryPublisher;
    private List<SwapBlotterPublisher> additionalPublishers;
    
    /**
     * Initialize the publisher service and select the appropriate publisher.
     */
    @jakarta.annotation.PostConstruct
    public void initialize() {
        MessagingProvider provider = determineProvider();
        log.info("Initializing messaging publisher service with provider: {}", provider);
        
        try {
            // Get primary publisher based on provider
            primaryPublisher = getPublisherForProvider(provider);
            if (primaryPublisher == null) {
                log.warn("No primary publisher available for provider: {}", provider);
            } else {
                log.info("Primary publisher initialized: {}", primaryPublisher.getClass().getSimpleName());
            }
            
            // Get additional publishers (webhooks, etc.)
            additionalPublishers = new ArrayList<>();
            if (webhookEnabled) {
                SwapBlotterPublisher webhookPublisher = getBeanSafely("webhookSwapBlotterPublisher", WebhookSwapBlotterPublisher.class);
                if (webhookPublisher != null) {
                    additionalPublishers.add(webhookPublisher);
                    log.info("Webhook publisher enabled");
                }
            }
            
            log.info("Messaging publisher service initialized with {} additional publisher(s)", 
                additionalPublishers.size());
                
        } catch (Exception e) {
            log.error("Failed to initialize messaging publisher service with provider: {}", provider, e);
            throw new RuntimeException("Failed to initialize messaging publisher service", e);
        }
    }
    
    /**
     * Publish SwapBlotter to the configured messaging provider and any additional publishers.
     * 
     * @param swapBlotter The SwapBlotter to publish
     */
    public void publish(SwapBlotter swapBlotter) {
        if (swapBlotter == null) {
            log.warn("Attempted to publish null SwapBlotter");
            return;
        }
        
        // Publish to primary provider
        if (primaryPublisher != null) {
            try {
                primaryPublisher.publish(swapBlotter);
                log.debug("Published SwapBlotter to primary publisher: tradeId={}, publisher={}", 
                    swapBlotter.getTradeId(), primaryPublisher.getClass().getSimpleName());
            } catch (Exception e) {
                log.error("Error publishing SwapBlotter to primary publisher: tradeId={}", 
                    swapBlotter.getTradeId(), e);
                // Don't throw - continue with additional publishers
            }
        } else {
            log.warn("No primary publisher available, skipping SwapBlotter publication: tradeId={}", 
                swapBlotter.getTradeId());
        }
        
        // Publish to additional publishers (webhooks, etc.)
        for (SwapBlotterPublisher publisher : additionalPublishers) {
            try {
                publisher.publish(swapBlotter);
                log.debug("Published SwapBlotter to additional publisher: tradeId={}, publisher={}", 
                    swapBlotter.getTradeId(), publisher.getClass().getSimpleName());
            } catch (Exception e) {
                log.error("Error publishing SwapBlotter to additional publisher {}: tradeId={}", 
                    publisher.getClass().getSimpleName(), swapBlotter.getTradeId(), e);
                // Continue with other publishers even if one fails
            }
        }
    }
    
    /**
     * Get the primary publisher instance.
     */
    public SwapBlotterPublisher getPrimaryPublisher() {
        return primaryPublisher;
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
     * Get the appropriate publisher bean for the specified provider.
     */
    private SwapBlotterPublisher getPublisherForProvider(MessagingProvider provider) {
        switch (provider) {
            case KAFKA:
                return getBeanSafely("kafkaSwapBlotterPublisher", KafkaSwapBlotterPublisher.class);
            case SOLACE:
                return getBeanSafely("solaceSwapBlotterPublisher", SolaceSwapBlotterPublisher.class);
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

