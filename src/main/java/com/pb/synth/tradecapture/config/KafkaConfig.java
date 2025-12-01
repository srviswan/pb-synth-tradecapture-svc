package com.pb.synth.tradecapture.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka configuration for trade message consumption.
 * Only active when messaging.kafka.enabled=true
 */
@Configuration
@ConditionalOnProperty(name = "messaging.kafka.enabled", havingValue = "true")
public class KafkaConfig {
    
    @Value("${messaging.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;
    
    @Value("${messaging.kafka.consumer.group-id:pb-synth-tradecapture-svc}")
    private String groupId;
    
    @Value("${messaging.kafka.consumer.auto-offset-reset:earliest}")
    private String autoOffsetReset;
    
    @Value("${messaging.kafka.consumer.enable-auto-commit:false}")
    private boolean enableAutoCommit;
    
    @Value("${messaging.kafka.consumer.max-poll-records:10}")
    private int maxPollRecords;
    
    @Value("${messaging.kafka.consumer.partition-assignment-strategy:org.apache.kafka.clients.consumer.StickyAssignor}")
    private String partitionAssignmentStrategy;
    
    @Value("${messaging.kafka.consumer.concurrency:3}")
    private int concurrency;
    
    @Bean
    public ConsumerFactory<String, byte[]> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, enableAutoCommit);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, maxPollRecords);
        
        // Consumer reliability settings
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30000);
        props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 10000);
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 300000);
        
        // Backpressure settings - reduce fetch size when under pressure
        // Smaller fetch size means less memory usage and faster processing
        props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1); // Minimum bytes to fetch
        props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 500); // Max wait time for fetch
        props.put(ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG, 1048576); // 1MB per partition
        
        // Partition assignment strategy for partition affinity
        // StickyAssignor provides better partition affinity during rebalancing
        // This ensures same partition key messages stay on same consumer instance
        props.put(ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG, 
            java.util.Collections.singletonList(partitionAssignmentStrategy));
        
        return new DefaultKafkaConsumerFactory<>(props);
    }
    
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, byte[]> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, byte[]> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        
        // Set concurrency for parallel partition processing
        // Each partition will be processed by a separate thread
        // Concurrency should match or be less than the number of partitions
        // For horizontal scaling, each instance should process multiple partitions
        factory.setConcurrency(concurrency);
        
        // Configure partition assignment for better load distribution
        // Messages with same partition key will go to same partition
        // Different partitions can be processed in parallel
        
        // Manual acknowledgment for better control
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        
        // Error handling - using DefaultErrorHandler
        // In production, you might want to configure a custom error handler with DLQ
        // factory.setCommonErrorHandler(new DefaultErrorHandler(...));
        
        return factory;
    }
}

