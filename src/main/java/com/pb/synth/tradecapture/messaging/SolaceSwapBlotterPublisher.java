package com.pb.synth.tradecapture.messaging;

import com.pb.synth.tradecapture.model.SwapBlotter;
import com.pb.synth.tradecapture.proto.TradeCaptureProto;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.jms.BytesMessage;
import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.DeliveryMode;
import jakarta.jms.JMSException;
import jakarta.jms.MessageProducer;
import jakarta.jms.Session;
import jakarta.jms.Topic;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Solace publisher for SwapBlotter.
 * Publishes to partition-specific output topics for downstream ordering.
 * 
 * Architecture:
 * - Publishes to: trade/capture/blotter/{partitionKey}
 * - Maintains partition key in output for downstream ordering
 * - Each partition gets its own topic for ordered delivery
 */
@Component
@ConditionalOnProperty(name = "messaging.solace.enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
@Slf4j
public class SolaceSwapBlotterPublisher implements SwapBlotterPublisher {

    private final ConnectionFactory solaceConnectionFactory;
    private final MessageConverter messageConverter;

    @Value("${messaging.solace.topics.output-pattern:trade/capture/blotter/{partitionKey}}")
    private String outputTopicPattern;
    
    // Legacy queue configuration (for backward compatibility)
    @Value("${messaging.solace.queues.output:trade/capture/blotter}")
    private String outputQueue;

    private Connection connection;
    private Session session;
    private MessageProducer producer;

    @PostConstruct
    public void initialize() {
        try {
            log.info("Initializing SolaceSwapBlotterPublisher");
            
            // Check if connection factory is available
            if (solaceConnectionFactory == null) {
                log.warn("Solace ConnectionFactory is not available. Publisher will not be initialized.");
                return;
            }
            
            // Create connection
            connection = solaceConnectionFactory.createConnection();
            connection.start();
            
            // Create session (AUTO_ACKNOWLEDGE for publishing)
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            
            // Create generic producer (topics created dynamically)
            producer = session.createProducer(null);
            producer.setDeliveryMode(DeliveryMode.PERSISTENT); // Persistent delivery
            producer.setTimeToLive(0); // No expiration
            
            log.info("SolaceSwapBlotterPublisher initialized successfully");
        } catch (JMSException e) {
            log.warn("Failed to initialize SolaceSwapBlotterPublisher (this is OK in test environments without Solace): {}", e.getMessage());
            // Don't throw exception - allow tests to run without Solace infrastructure
            // The publisher will just not be available
        } catch (Exception e) {
            log.warn("Unexpected error initializing SolaceSwapBlotterPublisher (this is OK in test environments): {}", e.getMessage());
            // Don't throw exception - allow tests to run
        }
    }

    @Override
    public void publish(SwapBlotter swapBlotter) {
        String partitionKey = swapBlotter.getPartitionKey();
        String outputTopic = getPartitionOutputTopic(partitionKey);
        
        log.info("Publishing SwapBlotter to Solace topic {}: tradeId={}, partitionKey={}", 
            outputTopic, swapBlotter.getTradeId(), partitionKey);
        
        try {
            // Convert SwapBlotter to protobuf
            TradeCaptureProto.SwapBlotterMessage protoMessage = 
                messageConverter.toSwapBlotterMessage(swapBlotter);
            
            // Serialize to bytes
            byte[] messageBytes = protoMessage.toByteArray();
            
            // Create partition-specific topic
            Topic topic = session.createTopic(outputTopic);
            
            // Create bytes message
            BytesMessage message = session.createBytesMessage();
            message.writeBytes(messageBytes);
            
            // Set message properties
            message.setStringProperty("partitionKey", partitionKey);
            message.setStringProperty("tradeId", swapBlotter.getTradeId());
            message.setStringProperty("messageType", "SwapBlotterMessage");
            message.setJMSCorrelationID(swapBlotter.getTradeId());
            
            // Publish message
            producer.send(topic, message);
            
            log.debug("Successfully published SwapBlotter to Solace topic: tradeId={}, partitionKey={}, topic={}", 
                swapBlotter.getTradeId(), partitionKey, outputTopic);
            
        } catch (Exception e) {
            log.error("Error publishing SwapBlotter to Solace topic {}: tradeId={}", 
                outputTopic, swapBlotter.getTradeId(), e);
            throw new RuntimeException("Failed to publish SwapBlotter to Solace topic", e);
        }
    }
    
    /**
     * Get partition-specific output topic name.
     * 
     * @param partitionKey The partition key (e.g., "ACC-001_BOOK-001_SEC-001")
     * @return Topic name (e.g., "trade/capture/blotter/ACC-001_BOOK-001_SEC-001")
     */
    private String getPartitionOutputTopic(String partitionKey) {
        if (partitionKey == null || partitionKey.isEmpty()) {
            log.warn("Partition key is null or empty, using default output queue: {}", outputQueue);
            return outputQueue;
        }
        
        // Sanitize partition key for topic name
        String sanitizedKey = sanitizeTopicName(partitionKey);
        return outputTopicPattern.replace("{partitionKey}", sanitizedKey);
    }
    
    /**
     * Sanitize partition key for use in topic name.
     * Solace topic names can contain: letters, numbers, hyphens, underscores, forward slashes
     * 
     * @param partitionKey The partition key
     * @return Sanitized key safe for topic names
     */
    private String sanitizeTopicName(String partitionKey) {
        if (partitionKey == null) {
            return "unknown";
        }
        // Replace any characters that might be problematic in topic names
        // Keep alphanumeric, underscore, hyphen, and forward slash
        return partitionKey.replaceAll("[^a-zA-Z0-9_\\-/]", "_");
    }
    
    @PreDestroy
    public void cleanup() {
        try {
            log.info("Cleaning up SolaceSwapBlotterPublisher resources");
            if (producer != null) {
                producer.close();
            }
            if (session != null) {
                session.close();
            }
            if (connection != null) {
                connection.close();
            }
            log.info("SolaceSwapBlotterPublisher cleanup completed");
        } catch (JMSException e) {
            log.error("Error cleaning up SolaceSwapBlotterPublisher resources", e);
        }
    }
}

