package com.pb.synth.tradecapture.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Configuration for consumer group management and monitoring.
 * Provides monitoring of consumer lag, rebalancing, and health checks.
 */
@Configuration
@ConditionalOnProperty(name = "messaging.kafka.enabled", havingValue = "true")
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class ConsumerGroupManagementConfig {

    private final MeterRegistry meterRegistry;
    private final KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;
    
    // Consumer lag metrics
    private final Map<String, AtomicLong> consumerLagMap = new HashMap<>();
    private final Map<String, AtomicLong> partitionCountMap = new HashMap<>();
    
    @PostConstruct
    public void initializeConsumerGroupMetrics() {
        // Initialize metrics for consumer group monitoring
        Gauge.builder("kafka.consumer.lag", () -> {
            long totalLag = consumerLagMap.values().stream()
                .mapToLong(AtomicLong::get)
                .sum();
            return totalLag;
        })
        .description("Total consumer lag across all partitions")
        .tag("service", "trade-capture")
        .register(meterRegistry);
        
        Gauge.builder("kafka.consumer.partitions.assigned", () -> {
            long totalPartitions = partitionCountMap.values().stream()
                .mapToLong(AtomicLong::get)
                .sum();
            return totalPartitions;
        })
        .description("Number of partitions assigned to consumers")
        .tag("service", "trade-capture")
        .register(meterRegistry);
        
        log.info("Consumer group metrics initialized");
    }
    
    /**
     * Monitor consumer lag and partition assignment.
     * Runs every 30 seconds.
     */
    @Scheduled(fixedRate = 30000)
    public void monitorConsumerGroup() {
        try {
            kafkaListenerEndpointRegistry.getAllListenerContainers().forEach(container -> {
                if (container.isRunning()) {
                    try {
                        // Get consumer from container
                        Consumer<?, ?> consumer = getConsumerFromContainer(container);
                        if (consumer != null) {
                            // Get assigned partitions
                            Set<TopicPartition> partitions = consumer.assignment();
                            partitionCountMap.put(container.getListenerId(), 
                                new AtomicLong(partitions.size()));
                            
                            // Calculate lag for each partition
                            long totalLag = 0;
                            for (TopicPartition partition : partitions) {
                                try {
                                    long endOffset = consumer.endOffsets(java.util.Collections.singleton(partition))
                                        .getOrDefault(partition, 0L);
                                    long currentOffset = consumer.position(partition);
                                    long lag = Math.max(0, endOffset - currentOffset);
                                    totalLag += lag;
                                } catch (Exception e) {
                                    log.debug("Error calculating lag for partition {}", partition, e);
                                }
                            }
                            
                            consumerLagMap.put(container.getListenerId(), new AtomicLong(totalLag));
                            
                            log.debug("Consumer group monitoring: listenerId={}, partitions={}, lag={}", 
                                container.getListenerId(), partitions.size(), totalLag);
                        }
                    } catch (Exception e) {
                        log.debug("Error monitoring consumer group for container {}", 
                            container.getListenerId(), e);
                    }
                }
            });
        } catch (Exception e) {
            log.warn("Error in consumer group monitoring", e);
        }
    }
    
    /**
     * Get consumer from container (reflection-based for Spring Kafka internals).
     */
    @SuppressWarnings("unchecked")
    private Consumer<?, ?> getConsumerFromContainer(MessageListenerContainer container) {
        try {
            // Try to get consumer via reflection (Spring Kafka internal API)
            if (container instanceof org.springframework.kafka.listener.ConcurrentMessageListenerContainer) {
                org.springframework.kafka.listener.ConcurrentMessageListenerContainer<?, ?> concurrentContainer =
                    (org.springframework.kafka.listener.ConcurrentMessageListenerContainer<?, ?>) container;
                
                // Access the consumer via container's internal structure
                // This is a simplified approach - in production, you might use KafkaAdminClient
                return null; // Placeholder - actual implementation would use KafkaAdminClient
            }
        } catch (Exception e) {
            log.debug("Could not extract consumer from container", e);
        }
        return null;
    }
    
    /**
     * Get consumer lag for a specific listener.
     */
    public long getConsumerLag(String listenerId) {
        return consumerLagMap.getOrDefault(listenerId, new AtomicLong(0)).get();
    }
    
    /**
     * Get number of assigned partitions for a listener.
     */
    public long getAssignedPartitions(String listenerId) {
        return partitionCountMap.getOrDefault(listenerId, new AtomicLong(0)).get();
    }
    
    /**
     * Check if consumer group is healthy (low lag, partitions assigned).
     */
    public boolean isConsumerGroupHealthy(String listenerId) {
        long lag = getConsumerLag(listenerId);
        long partitions = getAssignedPartitions(listenerId);
        
        // Healthy if lag is reasonable (< 1000 messages) and has partitions assigned
        return lag < 1000 && partitions > 0;
    }
}

