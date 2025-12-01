package com.pb.synth.tradecapture.messaging;

import com.pb.synth.tradecapture.model.TradeCaptureRequest;
import com.pb.synth.tradecapture.proto.TradeCaptureProto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

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
    
    @Autowired(required = false)
    private com.pb.synth.tradecapture.messaging.ConsumerLagMonitor consumerLagMonitor;
    
    @Autowired(required = false)
    private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;
    
    @Value("${messaging.kafka.topics.input:trade-capture-input}")
    private String inputTopic;
    
    @Value("${messaging.kafka.consumer.group-id:pb-synth-tradecapture-svc}")
    private String groupId;
    
    private volatile boolean running = false;
    
    @PostConstruct
    public void init() {
        // Wire up consumer lag monitor to listener container
        if (consumerLagMonitor != null && kafkaListenerEndpointRegistry != null) {
            String listenerId = kafkaListenerEndpointRegistry.getListenerContainers().stream()
                .filter(container -> container.getListenerId() != null && 
                        container.getListenerId().contains("trade-capture-input"))
                .findFirst()
                .map(MessageListenerContainer::getListenerId)
                .orElse(null);
            
            if (listenerId != null) {
                MessageListenerContainer container = kafkaListenerEndpointRegistry.getListenerContainer(listenerId);
                if (container != null) {
                    consumerLagMonitor.setListenerContainer(container);
                    log.info("Wired ConsumerLagMonitor to listener container: {}", listenerId);
                }
            }
        }
    }
    
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
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        try {
            log.debug("Received trade message from Kafka: topic={}, partition={}, key={}, offset={}", 
                topic, partition, key, offset);
            
            // Deserialize protobuf message
            TradeCaptureProto.TradeCaptureMessage protoMessage = 
                TradeCaptureProto.TradeCaptureMessage.parseFrom(messageBytes);
            
            log.info("Processing trade message from Kafka: tradeId={}, partitionKey={}", 
                protoMessage.getTradeId(), protoMessage.getPartitionKey());
            
            // Convert to TradeCaptureRequest (includes job metadata extraction)
            TradeCaptureRequest request = messageConverter.toTradeCaptureRequest(protoMessage);
            
            // Extract metadata for job tracking and webhooks
            // Job metadata is already included in the TradeCaptureRequest via MessageConverter
            // We pass the metadata map for additional context
            java.util.Map<String, String> metadata = new java.util.HashMap<>(protoMessage.getMetadataMap());
            
            // Process the trade with metadata
            messageProcessor.processMessage(request, metadata);
            
            // Acknowledge message after successful processing
            if (acknowledgment != null) {
                acknowledgment.acknowledge();
            }
            
            log.info("Successfully processed trade from Kafka: tradeId={}", request.getTradeId());
            
        } catch (Exception e) {
            log.error("Error processing trade message from Kafka: topic={}, partition={}, offset={}", 
                topic, partition, offset, e);
            
            // Convert to TradeCaptureRequest for DLQ
            try {
                TradeCaptureProto.TradeCaptureMessage protoMessage = 
                    TradeCaptureProto.TradeCaptureMessage.parseFrom(messageBytes);
                TradeCaptureRequest request = messageConverter.toTradeCaptureRequest(protoMessage);
                
                // Publish to DLQ
                dlqPublisher.publishToDLQ(request, e);
            } catch (Exception dlqError) {
                log.error("Failed to publish to DLQ, original error: {}", e.getMessage(), dlqError);
            }
            
            // Don't throw - acknowledge to prevent infinite retries
            // DLQ will handle retry manually
            if (acknowledgment != null) {
                acknowledgment.acknowledge();
            }
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

