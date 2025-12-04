package com.pb.synth.tradecapture.messaging;

import com.pb.synth.tradecapture.proto.TradeCaptureProto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Kafka implementation of TradeInputPublisher.
 * Publishes trade input messages to Kafka topic with partition key routing.
 */
@Component
@ConditionalOnProperty(name = "messaging.kafka.enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
@Slf4j
public class KafkaTradeInputPublisher implements TradeInputPublisher {
    
    private final KafkaTemplate<String, byte[]> kafkaTemplate;
    
    @Value("${messaging.kafka.topics.input:trade-capture-input}")
    private String inputTopic;
    
    @Override
    public void publish(TradeCaptureProto.TradeCaptureMessage protoMessage, String partitionKey) {
        log.info("Publishing trade to Kafka input topic {}: tradeId={}, partitionKey={}", 
            inputTopic, protoMessage.getTradeId(), partitionKey);
        
        try {
            // Serialize protobuf to bytes
            byte[] messageBytes = protoMessage.toByteArray();
            
            // Publish to Kafka with partition key as message key
            // This ensures messages with same partition key go to same partition
            CompletableFuture<SendResult<String, byte[]>> future = 
                kafkaTemplate.send(inputTopic, partitionKey, messageBytes);
            
            future.whenComplete((result, exception) -> {
                if (exception != null) {
                    log.error("Error publishing trade to Kafka: tradeId={}, partitionKey={}", 
                        protoMessage.getTradeId(), partitionKey, exception);
                } else {
                    log.info("Successfully published trade to Kafka: tradeId={}, partition={}, offset={}", 
                        protoMessage.getTradeId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
                }
            });
            
        } catch (Exception e) {
            log.error("Error publishing trade to Kafka input topic {}: tradeId={}", 
                inputTopic, protoMessage.getTradeId(), e);
            throw new RuntimeException("Failed to publish trade to Kafka input topic", e);
        }
    }
    
    @Override
    public boolean isAvailable() {
        // KafkaTemplate is always available if bean is created
        return kafkaTemplate != null;
    }
}

