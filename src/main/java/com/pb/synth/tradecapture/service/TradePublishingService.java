package com.pb.synth.tradecapture.service;

import com.pb.synth.tradecapture.messaging.MessageConverter;
import com.pb.synth.tradecapture.model.TradeCaptureRequest;
import com.pb.synth.tradecapture.proto.TradeCaptureProto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Service for publishing trades to message queue for async processing.
 * This service publishes API-initiated trades to the same queue as message-based input.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TradePublishingService {
    
    private final MessageConverter messageConverter;
    private final KafkaTemplate<String, byte[]> kafkaTemplate;
    
    @Value("${messaging.kafka.topics.input:trade-capture-input}")
    private String inputTopic;
    
    /**
     * Publish a single trade to the message queue for async processing.
     * 
     * @param request The trade capture request
     * @param jobId The job ID for tracking (if null, generates one)
     * @param sourceApi The source API (REST_API, MANUAL_ENTRY, FILE_UPLOAD)
     * @param callbackUrl Optional webhook callback URL
     * @return The job ID
     */
    public String publishTrade(TradeCaptureRequest request, String jobId, 
                               String sourceApi, String callbackUrl) {
        final String finalJobId = (jobId == null) ? UUID.randomUUID().toString() : jobId;
        
        try {
            log.info("Publishing trade to queue: tradeId={}, jobId={}, sourceApi={}", 
                request.getTradeId(), finalJobId, sourceApi);
            
            // Convert to protobuf
            TradeCaptureProto.TradeCaptureMessage protoMessage = 
                messageConverter.toProtobufMessage(request);
            
            // Add job metadata
            TradeCaptureProto.TradeCaptureMessage.Builder builder = protoMessage.toBuilder();
            builder.putMetadata("job_id", finalJobId);
            builder.putMetadata("source_api", sourceApi);
            if (callbackUrl != null && !callbackUrl.isEmpty()) {
                builder.putMetadata("callback_url", callbackUrl);
            }
            builder.putMetadata("publish_timestamp", String.valueOf(System.currentTimeMillis()));
            
            protoMessage = builder.build();
            
            // Publish to Kafka with partition key as message key
            String partitionKey = request.getPartitionKey() != null 
                ? request.getPartitionKey() 
                : "unknown";
            byte[] messageBytes = protoMessage.toByteArray();
            
            kafkaTemplate.send(inputTopic, partitionKey, messageBytes)
                .whenComplete((result, exception) -> {
                    if (exception != null) {
                        log.error("Error publishing trade to queue: tradeId={}, jobId={}", 
                            request.getTradeId(), finalJobId, exception);
                    } else {
                        log.info("Successfully published trade to queue: tradeId={}, jobId={}, partition={}, offset={}", 
                            request.getTradeId(), finalJobId,
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                    }
                });
            
            return finalJobId;
            
        } catch (Exception e) {
            log.error("Error publishing trade to queue: tradeId={}, jobId={}", 
                request.getTradeId(), jobId, e);
            throw new RuntimeException("Failed to publish trade to queue", e);
        }
    }
}

