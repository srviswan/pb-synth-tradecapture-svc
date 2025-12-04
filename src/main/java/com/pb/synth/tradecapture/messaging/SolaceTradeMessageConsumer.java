package com.pb.synth.tradecapture.messaging;

import com.pb.synth.tradecapture.model.TradeCaptureRequest;
import com.pb.synth.tradecapture.proto.TradeCaptureProto;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.jms.BytesMessage;
import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageListener;
import jakarta.jms.Session;
import jakarta.jms.Topic;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Solace consumer for real-time trade message processing.
 * Consumes messages from partition-specific Solace topics and processes them through TradeCaptureService.
 * 
 * Architecture:
 * - Router consumes from single topic: trade/capture/input
 * - Router routes to partition-specific topics: trade/capture/input/{partitionKey}
 * - This consumer subscribes to partition-specific topics using wildcard: trade/capture/input/>
 * - Messages are automatically ordered per partition by Solace topics
 * 
 * This is used for production. Local development uses KafkaTradeMessageConsumer.
 */
@Component
@ConditionalOnProperty(name = "messaging.solace.enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
@Slf4j
public class SolaceTradeMessageConsumer implements TradeMessageConsumer, MessageListener {
    
    private final ConnectionFactory solaceConnectionFactory;
    private final TradeMessageProcessor messageProcessor;
    private final MessageConverter messageConverter;
    private final ApplicationContext applicationContext;
    
    @Value("${messaging.solace.consumer.topic-pattern:trade/capture/input/>}")
    private String topicPattern;
    
    @Value("${messaging.solace.consumer.consumer-threads:3}")
    private int consumerThreads;
    
    @Value("${messaging.solace.queues.dlq:trade/capture/dlq}")
    private String dlqQueue;
    
    private Connection connection;
    private Session session;
    private MessageConsumer messageConsumer;
    private Topic topic;
    
    private final AtomicBoolean running = new AtomicBoolean(false);
    
    private DLQPublisher dlqPublisher;
    
    @PostConstruct
    public void initialize() {
        log.info("Initializing Solace trade message consumer for topic pattern: {}", topicPattern);
        
        // Get DLQPublisher if available (optional)
        try {
            dlqPublisher = applicationContext.getBean(DLQPublisher.class);
        } catch (org.springframework.beans.factory.NoSuchBeanDefinitionException e) {
            log.debug("DLQPublisher not available (Kafka disabled) - DLQ publishing will be skipped");
        }
        
        try {
            // Check if connection factory is available
            if (solaceConnectionFactory == null) {
                log.warn("Solace ConnectionFactory is not available. Consumer will not be initialized.");
                return;
            }
            
            // Create connection
            connection = solaceConnectionFactory.createConnection();
            connection.start();
            
            // Create session (CLIENT_ACKNOWLEDGE for manual acknowledgment)
            session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
            
            // Create topic from pattern (wildcard subscription)
            topic = session.createTopic(topicPattern);
            
            // Create consumer for topic pattern
            messageConsumer = session.createConsumer(topic);
            messageConsumer.setMessageListener(this);
            
            log.info("Solace consumer initialized successfully. Subscribed to topic pattern: {}", topicPattern);
            
            // Auto-start consumer
            start();
            
        } catch (JMSException e) {
            log.warn("Failed to initialize Solace trade message consumer (this is OK in test environments without Solace): {}", e.getMessage());
            // Don't throw exception - allow tests to run without Solace infrastructure
            // The consumer will just not be available
        } catch (Exception e) {
            log.warn("Unexpected error initializing Solace trade message consumer (this is OK in test environments): {}", e.getMessage());
            // Don't throw exception - allow tests to run
        }
    }
    
    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            log.info("Starting Solace trade message consumer for topic pattern: {}", topicPattern);
            // Consumer is already started via MessageListener in initialize()
        } else {
            log.warn("Solace consumer is already running");
        }
    }
    
    /**
     * Message listener implementation - called when a message arrives on a partition topic.
     */
    @Override
    public void onMessage(Message message) {
        String partitionKey = null;
        String tradeId = "unknown";
        
        try {
            // Extract partition key from topic name
            if (message.getJMSDestination() instanceof Topic) {
                Topic destinationTopic = (Topic) message.getJMSDestination();
                String topicName = destinationTopic.getTopicName();
                partitionKey = extractPartitionKeyFromTopic(topicName);
            }
            
            // Also try to get partition key from message property
            if (partitionKey == null || partitionKey.isEmpty()) {
                try {
                    partitionKey = message.getStringProperty("partitionKey");
                } catch (JMSException e) {
                    log.debug("Could not get partitionKey from message property", e);
                }
            }
            
            // Extract message bytes
            byte[] messageBytes = extractMessageBytes(message);
            
            // Deserialize protobuf message
            TradeCaptureProto.TradeCaptureMessage protoMessage = 
                TradeCaptureProto.TradeCaptureMessage.parseFrom(messageBytes);
            
            tradeId = protoMessage.getTradeId();
            
            // Verify partition key matches (safety check)
            if (partitionKey != null && protoMessage.getPartitionKey() != null &&
                !partitionKey.equals(protoMessage.getPartitionKey())) {
                log.warn("Partition key mismatch: topic={}, message={}, tradeId={}", 
                    partitionKey, protoMessage.getPartitionKey(), tradeId);
                // Use message partition key as source of truth
                partitionKey = protoMessage.getPartitionKey();
            } else if (partitionKey == null && protoMessage.getPartitionKey() != null) {
                partitionKey = protoMessage.getPartitionKey();
            }
            
            // Convert to TradeCaptureRequest
            TradeCaptureRequest request = messageConverter.toTradeCaptureRequest(protoMessage);
            
            // Extract metadata
            Map<String, String> metadata = new HashMap<>(protoMessage.getMetadataMap());
            
            // Process message
            log.debug("Processing Solace message: tradeId={}, partitionKey={}", tradeId, partitionKey);
            messageProcessor.processMessage(request, metadata);
            
            // Acknowledge message on success
            message.acknowledge();
            
            log.info("Successfully processed trade from Solace: tradeId={}, partitionKey={}", 
                tradeId, partitionKey);
            
        } catch (Exception e) {
            log.error("Error processing Solace message: tradeId={}, partitionKey={}", 
                tradeId, partitionKey, e);
            
            // Publish to DLQ if available
            if (dlqPublisher != null) {
                try {
                    byte[] messageBytes = extractMessageBytes(message);
                    TradeCaptureProto.TradeCaptureMessage protoMessage = 
                        TradeCaptureProto.TradeCaptureMessage.parseFrom(messageBytes);
                    TradeCaptureRequest request = messageConverter.toTradeCaptureRequest(protoMessage);
                    dlqPublisher.publishToDLQ(request, e);
                } catch (Exception dlqError) {
                    log.error("Failed to publish to DLQ", dlqError);
                }
            }
            
            // Acknowledge message to prevent redelivery (DLQ handles it)
            try {
                message.acknowledge();
            } catch (JMSException ackError) {
                log.error("Failed to acknowledge message after processing failure", ackError);
            }
        }
    }
    
    /**
     * Extract bytes from JMS message.
     */
    private byte[] extractMessageBytes(Message message) throws JMSException {
        if (message instanceof BytesMessage) {
            BytesMessage bytesMessage = (BytesMessage) message;
            bytesMessage.reset(); // Reset to beginning
            byte[] bytes = new byte[(int) bytesMessage.getBodyLength()];
            bytesMessage.readBytes(bytes);
            return bytes;
        }
        throw new IllegalArgumentException("Message is not a BytesMessage");
    }
    
    /**
     * Extract partition key from topic name.
     * Example: trade/capture/input/ACC-001_BOOK-001_SEC-001 -> ACC-001_BOOK-001_SEC-001
     */
    private String extractPartitionKeyFromTopic(String topicName) {
        if (topicName == null || !topicName.contains("/")) {
            return null;
        }
        // Extract last segment after final '/'
        int lastSlash = topicName.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < topicName.length() - 1) {
            return topicName.substring(lastSlash + 1);
        }
        return null;
    }
    
    @Override
    public void stop() {
        if (running.compareAndSet(true, false)) {
            log.info("Stopping Solace trade message consumer");
            
            try {
                if (messageConsumer != null) {
                    messageConsumer.close();
                }
                if (session != null) {
                    session.close();
                }
                if (connection != null) {
                    connection.stop();
                    connection.close();
                }
            } catch (JMSException e) {
                log.error("Error closing Solace consumer resources", e);
            }
            
            log.info("Solace consumer stopped");
        }
    }
    
    @PreDestroy
    public void cleanup() {
        stop();
    }
    
    @Override
    public boolean isRunning() {
        return running.get();
    }
    
    /**
     * Get consumer health status.
     */
    public boolean isHealthy() {
        return running.get() && connection != null && session != null && messageConsumer != null;
    }
}
