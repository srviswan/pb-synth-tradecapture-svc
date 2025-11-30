package com.pb.synth.tradecapture.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pb.synth.tradecapture.model.SwapBlotter;
import com.pb.synth.tradecapture.proto.TradeCaptureProto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Kafka publisher for SwapBlotter.
 * Publishes to Kafka topic with partition key for ordered processing.
 */
@Component
@ConditionalOnProperty(name = "messaging.kafka.enabled", havingValue = "true")
@Slf4j
public class KafkaSwapBlotterPublisher implements SwapBlotterPublisher {

    private final KafkaTemplate<String, byte[]> kafkaTemplate;
    private final MessageConverter messageConverter;
    private final ObjectMapper objectMapper;
    
    @Autowired
    public KafkaSwapBlotterPublisher(
            @Qualifier("kafkaTemplateWithPartitioning") KafkaTemplate<String, byte[]> kafkaTemplate,
            MessageConverter messageConverter,
            ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.messageConverter = messageConverter;
        this.objectMapper = objectMapper;
    }

    @Value("${messaging.kafka.topics.output:trade-capture-blotter}")
    private String outputTopic;

    @Value("${messaging.kafka.publish-format:protobuf}")
    private String publishFormat; // protobuf or json

    @Override
    public void publish(SwapBlotter swapBlotter) {
        try {
            log.debug("Publishing SwapBlotter to Kafka topic {}: {}", outputTopic, swapBlotter.getTradeId());
            
            // Use partition key as Kafka message key for partitioning
            String partitionKey = swapBlotter.getPartitionKey();
            byte[] messageBytes;
            
            if ("protobuf".equalsIgnoreCase(publishFormat)) {
                // Convert to protobuf
                TradeCaptureProto.SwapBlotterMessage protoMessage = 
                    messageConverter.toSwapBlotterMessage(swapBlotter);
                messageBytes = protoMessage.toByteArray();
            } else {
                // Convert to JSON
                messageBytes = objectMapper.writeValueAsBytes(swapBlotter);
            }
            
            // Publish with partition key as key
            // This ensures messages with same partition key go to same partition
            CompletableFuture<SendResult<String, byte[]>> future = 
                kafkaTemplate.send(outputTopic, partitionKey, messageBytes);
            
            future.whenComplete((result, exception) -> {
                if (exception != null) {
                    log.error("Error publishing SwapBlotter to Kafka: tradeId={}, partitionKey={}", 
                        swapBlotter.getTradeId(), partitionKey, exception);
                } else {
                    log.info("Successfully published SwapBlotter to Kafka: tradeId={}, partition={}, offset={}", 
                        swapBlotter.getTradeId(), 
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
                }
            });
            
        } catch (Exception e) {
            log.error("Error serializing/publishing SwapBlotter to Kafka: tradeId={}", 
                swapBlotter.getTradeId(), e);
            throw new RuntimeException("Failed to publish SwapBlotter to Kafka", e);
        }
    }
}

