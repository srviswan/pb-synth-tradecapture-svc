package com.pb.synth.tradecapture.messaging;

import com.pb.synth.tradecapture.model.TradeCaptureRequest;
import com.pb.synth.tradecapture.proto.TradeCaptureProto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publisher for Dead Letter Queue (DLQ).
 * Publishes failed messages to DLQ for manual review and retry.
 */
@Component
@ConditionalOnProperty(name = "messaging.kafka.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class DLQPublisher {
    
    private final MessageConverter messageConverter;
    private final KafkaTemplate<String, byte[]> kafkaTemplate;
    
    @Value("${messaging.kafka.topics.dlq:trade-capture-dlq}")
    private String dlqTopic;
    
    @Value("${messaging.solace.queues.dlq:trade/capture/dlq}")
    private String solaceDlqQueue;
    
    /**
     * Publish failed trade message to DLQ.
     * 
     * @param request The failed trade request
     * @param error The error that caused the failure
     */
    public void publishToDLQ(TradeCaptureRequest request, Throwable error) {
        try {
            log.warn("Publishing failed trade to DLQ: tradeId={}, partitionKey={}, error={}", 
                request.getTradeId(), request.getPartitionKey(), error.getMessage());
            
            // Convert to protobuf
            TradeCaptureProto.TradeCaptureMessage protoMessage = 
                messageConverter.toProtobufMessage(request);
            
            // Add error metadata
            TradeCaptureProto.TradeCaptureMessage.Builder builder = protoMessage.toBuilder();
            builder.putMetadata("dlq_error", error.getMessage());
            builder.putMetadata("dlq_timestamp", String.valueOf(System.currentTimeMillis()));
            builder.putMetadata("dlq_reason", error.getClass().getSimpleName());
            
            protoMessage = builder.build();
            
            // Publish to Kafka DLQ (if Kafka is enabled)
            // In production, you might also publish to Solace DLQ
            byte[] messageBytes = protoMessage.toByteArray();
            String partitionKey = request.getPartitionKey() != null ? request.getPartitionKey() : "unknown";
            kafkaTemplate.send(dlqTopic, partitionKey, messageBytes);
            
            log.info("Successfully published to DLQ: tradeId={}", request.getTradeId());
            
        } catch (Exception e) {
            log.error("Failed to publish to DLQ: tradeId={}", request.getTradeId(), e);
            // If DLQ publishing fails, we log it but don't throw
            // This prevents infinite loops
        }
    }
    
    /**
     * Publish failed protobuf message to DLQ.
     * 
     * @param protoMessage The failed protobuf message
     * @param error The error that caused the failure
     */
    public void publishToDLQ(TradeCaptureProto.TradeCaptureMessage protoMessage, Throwable error) {
        try {
            log.warn("Publishing failed protobuf message to DLQ: tradeId={}, error={}", 
                protoMessage.getTradeId(), error.getMessage());
            
            // Add error metadata
            TradeCaptureProto.TradeCaptureMessage.Builder builder = protoMessage.toBuilder();
            builder.putMetadata("dlq_error", error.getMessage());
            builder.putMetadata("dlq_timestamp", String.valueOf(System.currentTimeMillis()));
            builder.putMetadata("dlq_reason", error.getClass().getSimpleName());
            
            TradeCaptureProto.TradeCaptureMessage enrichedMessage = builder.build();
            
            // Publish to Kafka DLQ
            byte[] messageBytes = enrichedMessage.toByteArray();
            String partitionKey = protoMessage.getPartitionKey() != null && !protoMessage.getPartitionKey().isEmpty() 
                ? protoMessage.getPartitionKey() : "unknown";
            kafkaTemplate.send(dlqTopic, partitionKey, messageBytes);
            
            log.info("Successfully published to DLQ: tradeId={}", protoMessage.getTradeId());
            
        } catch (Exception e) {
            log.error("Failed to publish to DLQ: tradeId={}", protoMessage.getTradeId(), e);
        }
    }
}

