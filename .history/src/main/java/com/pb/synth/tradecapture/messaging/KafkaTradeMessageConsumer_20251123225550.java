package com.pb.synth.tradecapture.messaging;

import com.pb.synth.tradecapture.model.TradeCaptureRequest;
import com.pb.synth.tradecapture.proto.TradeCaptureProto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for real-time trade message processing.
 * Consumes messages from Kafka topic and processes them through TradeCaptureService.
 * 
 * This is used for local development. Production uses SolaceTradeMessageConsumer.
 */
@Component
@ConditionalOnProperty(name = "messaging.kafka.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class KafkaTradeMessageConsumer implements TradeMessageConsumer {
    
    private final TradeMessageProcessor messageProcessor;
    private final MessageConverter messageConverter;
    private final DLQPublisher dlqPublisher;
    
    @Value("${messaging.kafka.topics.input:trade-capture-input}")
    private String inputTopic;
    
    @Value("${messaging.kafka.consumer.group-id:pb-synth-tradecapture-svc}")
    private String groupId;
    
    private volatile boolean running = false;
    
    /**
     * Kafka listener for trade capture messages.
     * Messages are deserialized from protobuf format and processed.
     */
    @KafkaListener(
        topics = "${messaging.kafka.topics.input:trade-capture-input}",
        groupId = "${messaging.kafka.consumer.group-id:pb-synth-tradecapture-svc}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeTradeMessage(
            @Payload byte[] messageBytes,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        try {
            log.debug("Received trade message from Kafka: topic={}, partition={}, offset={}", 
                topic, partition, offset);
            
            // Deserialize protobuf message
            TradeCaptureProto.TradeCaptureMessage protoMessage = 
                TradeCaptureProto.TradeCaptureMessage.parseFrom(messageBytes);
            
            log.info("Processing trade message from Kafka: tradeId={}, partitionKey={}", 
                protoMessage.getTradeId(), protoMessage.getPartitionKey());
            
            // Convert to TradeCaptureRequest
            TradeCaptureRequest request = messageConverter.toTradeCaptureRequest(protoMessage);
            
            // Process the trade
            messageProcessor.processMessage(request);
            
            // Acknowledge message after successful processing
            if (acknowledgment != null) {
                acknowledgment.acknowledge();
            }
            
            log.info("Successfully processed trade from Kafka: tradeId={}", request.getTradeId());
            
        } catch (Exception e) {
            log.error("Error processing trade message from Kafka: topic={}, partition={}, offset={}", 
                topic, partition, offset, e);
            // In production, you might want to send to DLQ or retry
            // For now, we'll let Kafka handle retries via consumer configuration
            throw new RuntimeException("Failed to process Kafka message", e);
        }
    }
    
    @Override
    public void start() {
        log.info("Starting Kafka trade message consumer for topic: {}", inputTopic);
        running = true;
    }
    
    @Override
    public void stop() {
        log.info("Stopping Kafka trade message consumer");
        running = false;
    }
    
    @Override
    public boolean isRunning() {
        return running;
    }
}

