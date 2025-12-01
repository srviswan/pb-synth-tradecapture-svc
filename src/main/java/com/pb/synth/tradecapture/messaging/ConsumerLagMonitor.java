package com.pb.synth.tradecapture.messaging;

import com.pb.synth.tradecapture.service.backpressure.BackpressureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Monitors Kafka consumer lag and implements backpressure by pausing/resuming consumers.
 * 
 * When consumer lag exceeds thresholds:
 * - Pauses consumer to prevent overwhelming the system
 * - Resumes consumer when lag decreases
 */
@Component
@ConditionalOnProperty(name = "messaging.kafka.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class ConsumerLagMonitor {
    
    private final BackpressureService backpressureService;
    private final ConsumerFactory<String, byte[]> consumerFactory;
    
    @Value("${backpressure.messaging.enabled:true}")
    private boolean messagingBackpressureEnabled;
    
    @Value("${backpressure.messaging.lag-check-interval-ms:5000}")
    private long lagCheckIntervalMs;
    
    @Value("${backpressure.messaging.max-lag:10000}")
    private long maxConsumerLag;
    
    @Value("${backpressure.messaging.resume-lag:2000}")
    private long resumeConsumerLag; // Resume when lag drops below this
    
    @Value("${messaging.kafka.topics.input:trade-capture-input}")
    private String inputTopic;
    
    @Value("${messaging.kafka.consumer.group-id:pb-synth-tradecapture-svc}")
    private String groupId;
    
    private final AtomicBoolean consumerPaused = new AtomicBoolean(false);
    private MessageListenerContainer listenerContainer;
    
    /**
     * Set the listener container for pause/resume operations.
     */
    public void setListenerContainer(MessageListenerContainer container) {
        this.listenerContainer = container;
    }
    
    /**
     * Monitor consumer lag periodically and apply backpressure.
     */
    @Scheduled(fixedDelayString = "${backpressure.messaging.lag-check-interval-ms:5000}")
    public void monitorConsumerLag() {
        if (!messagingBackpressureEnabled || listenerContainer == null) {
            return;
        }
        
        try {
            long currentLag = calculateConsumerLag();
            backpressureService.updateConsumerLag(currentLag);
            
            boolean currentlyPaused = consumerPaused.get();
            
            if (currentLag >= maxConsumerLag && !currentlyPaused) {
                // Pause consumer to apply backpressure
                log.warn("Consumer lag exceeded threshold ({} >= {}), pausing consumer", 
                    currentLag, maxConsumerLag);
                pauseConsumer();
            } else if (currentLag < resumeConsumerLag && currentlyPaused) {
                // Resume consumer when lag decreases
                log.info("Consumer lag decreased below resume threshold ({} < {}), resuming consumer", 
                    currentLag, resumeConsumerLag);
                resumeConsumer();
            }
            
            // Log warning if lag is high but not yet at pause threshold
            if (currentLag >= maxConsumerLag * 0.8 && !currentlyPaused) {
                log.warn("Consumer lag approaching threshold: {} (threshold: {})", 
                    currentLag, maxConsumerLag);
            }
            
        } catch (Exception e) {
            log.error("Error monitoring consumer lag", e);
        }
    }
    
    /**
     * Calculate current consumer lag for all partitions.
     */
    private long calculateConsumerLag() {
        try (Consumer<String, byte[]> consumer = consumerFactory.createConsumer()) {
            // Get all partitions for the topic
            Set<TopicPartition> partitions = consumer.partitionsFor(inputTopic).stream()
                .map(partitionInfo -> new TopicPartition(inputTopic, partitionInfo.partition()))
                .collect(java.util.stream.Collectors.toSet());
            
            consumer.assign(partitions);
            
            // Get end offsets (latest available offsets)
            Map<TopicPartition, Long> endOffsets = consumer.endOffsets(partitions, Duration.ofSeconds(5));
            
            // Get committed offsets (consumer group's current position)
            Map<TopicPartition, Long> committedOffsets = new HashMap<>();
            try {
                var allCommittedOffsets = consumer.committed(partitions, Duration.ofSeconds(5));
                for (TopicPartition partition : partitions) {
                    if (allCommittedOffsets != null && allCommittedOffsets.containsKey(partition)) {
                        var metadata = allCommittedOffsets.get(partition);
                        if (metadata != null) {
                            committedOffsets.put(partition, metadata.offset());
                        } else {
                            committedOffsets.put(partition, 0L);
                        }
                    } else {
                        committedOffsets.put(partition, 0L);
                    }
                }
            } catch (Exception e) {
                log.debug("Could not get committed offsets", e);
                // Default all partitions to 0
                for (TopicPartition partition : partitions) {
                    committedOffsets.put(partition, 0L);
                }
            }
            
            // Calculate total lag across all partitions
            long totalLag = 0;
            for (TopicPartition partition : partitions) {
                long endOffset = endOffsets.getOrDefault(partition, 0L);
                long committedOffset = committedOffsets.getOrDefault(partition, 0L);
                long partitionLag = Math.max(0, endOffset - committedOffset);
                totalLag += partitionLag;
            }
            
            return totalLag;
            
        } catch (Exception e) {
            log.error("Error calculating consumer lag", e);
            return 0;
        }
    }
    
    /**
     * Pause the consumer to apply backpressure.
     */
    private void pauseConsumer() {
        if (listenerContainer != null && consumerPaused.compareAndSet(false, true)) {
            try {
                listenerContainer.pause();
                log.info("Consumer paused due to backpressure");
            } catch (Exception e) {
                log.error("Error pausing consumer", e);
                consumerPaused.set(false);
            }
        }
    }
    
    /**
     * Resume the consumer when backpressure conditions improve.
     */
    private void resumeConsumer() {
        if (listenerContainer != null && consumerPaused.compareAndSet(true, false)) {
            try {
                listenerContainer.resume();
                log.info("Consumer resumed after backpressure conditions improved");
            } catch (Exception e) {
                log.error("Error resuming consumer", e);
                consumerPaused.set(true);
            }
        }
    }
    
    /**
     * Check if consumer is currently paused.
     */
    public boolean isConsumerPaused() {
        return consumerPaused.get();
    }
}

