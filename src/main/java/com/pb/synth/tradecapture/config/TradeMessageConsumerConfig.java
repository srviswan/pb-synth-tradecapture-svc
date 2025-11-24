package com.pb.synth.tradecapture.config;

import com.pb.synth.tradecapture.messaging.TradeMessageConsumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Configuration component that starts the appropriate trade message consumer
 * based on configuration (Kafka for local, Solace for production).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TradeMessageConsumerConfig implements CommandLineRunner {
    
    private final ApplicationContext applicationContext;
    
    @Override
    public void run(String... args) {
        log.info("Starting trade message consumer based on configuration...");
        
        // Find all TradeMessageConsumer beans
        Map<String, TradeMessageConsumer> consumers = 
            applicationContext.getBeansOfType(TradeMessageConsumer.class);
        
        if (consumers.isEmpty()) {
            log.warn("No trade message consumer found. Trade processing from queues will not be available.");
            return;
        }
        
        // Start all active consumers (only one should be active based on configuration)
        consumers.forEach((beanName, consumer) -> {
            log.info("Starting trade message consumer: {}", beanName);
            try {
                consumer.start();
                log.info("Successfully started trade message consumer: {}", beanName);
            } catch (Exception e) {
                log.error("Failed to start trade message consumer: {}", beanName, e);
            }
        });
    }
}

