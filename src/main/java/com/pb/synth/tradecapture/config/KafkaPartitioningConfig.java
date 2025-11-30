package com.pb.synth.tradecapture.config;

import org.apache.kafka.clients.producer.Partitioner;
import org.apache.kafka.clients.producer.internals.DefaultPartitioner;
import org.apache.kafka.common.Cluster;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.utils.Utils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.Map;

/**
 * Configuration for Kafka partitioning strategy.
 * Ensures messages with the same partition key go to the same Kafka partition,
 * enabling parallel processing while maintaining order within a partition.
 */
@Configuration
@ConditionalOnProperty(name = "messaging.kafka.enabled", havingValue = "true")
public class KafkaPartitioningConfig {

    /**
     * Custom partitioner that uses partition key from message.
     * This ensures trades with the same partition key (Account/Book + Security)
     * are routed to the same Kafka partition for ordered processing.
     */
    public static class PartitionKeyPartitioner implements Partitioner {
        
        @Override
        public int partition(String topic, Object key, byte[] keyBytes, 
                           Object value, byte[] valueBytes, Cluster cluster) {
            List<PartitionInfo> partitions = cluster.partitionsForTopic(topic);
            int numPartitions = partitions.size();
            
            if (numPartitions == 0) {
                return 0;
            }
            
            // If key is provided, use it for partitioning
            if (key != null) {
                return Utils.toPositive(Utils.murmur2(keyBytes)) % numPartitions;
            }
            
            // Fallback to default partitioner if no key
            return new DefaultPartitioner().partition(topic, key, keyBytes, value, valueBytes, cluster);
        }
        
        @Override
        public void close() {
            // No resources to close
        }
        
        @Override
        public void configure(Map<String, ?> configs) {
            // No configuration needed
        }
    }
    
    /**
     * Kafka template with partition-aware producer.
     * This is an alias to the base kafkaTemplate - partitioning is handled by using partition key as message key.
     * The custom partitioner class is not needed since Kafka automatically partitions based on message key.
     * 
     * Note: This bean is only created when Kafka is enabled, and it references the base kafkaTemplate.
     */
    @Bean("kafkaTemplateWithPartitioning")
    @ConditionalOnProperty(name = "messaging.kafka.enabled", havingValue = "true")
    public KafkaTemplate<String, byte[]> kafkaTemplateWithPartitioning(
            @Qualifier("kafkaTemplate") KafkaTemplate<String, byte[]> baseTemplate) {
        // Return the base template - partitioning is handled by using partition key as message key
        // When you call kafkaTemplate.send(topic, partitionKey, message), Kafka automatically
        // routes messages with the same key to the same partition
        return baseTemplate;
    }
}

