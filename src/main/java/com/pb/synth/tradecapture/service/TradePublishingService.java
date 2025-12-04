package com.pb.synth.tradecapture.service;

import com.pb.synth.tradecapture.messaging.MessageConverter;
import com.pb.synth.tradecapture.messaging.TradeInputPublisher;
import com.pb.synth.tradecapture.messaging.TradeInputPublisherFactory;
import com.pb.synth.tradecapture.model.TradeCaptureRequest;
import com.pb.synth.tradecapture.proto.TradeCaptureProto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Service for publishing trades to message queue for async processing.
 * This service publishes API-initiated trades to the same queue as message-based input.
 * 
 * Uses TradeInputPublisher interface to abstract the underlying messaging implementation
 * (Kafka, Solace, etc.) from the service layer.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TradePublishingService {
    
    private final MessageConverter messageConverter;
    private final TradeInputPublisherFactory publisherFactory;
    
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
            
            // Extract partition key
            String partitionKey = request.getPartitionKey() != null 
                ? request.getPartitionKey() 
                : "unknown";
            
            // Get the configured publisher (abstracts Kafka vs Solace)
            Optional<TradeInputPublisher> publisherOpt = publisherFactory.getPublisher();
            
            if (publisherOpt.isPresent()) {
                TradeInputPublisher publisher = publisherOpt.get();
                log.info("Publishing trade via {}: tradeId={}, partitionKey={}", 
                    publisher.getClass().getSimpleName(), request.getTradeId(), partitionKey);
                publisher.publish(protoMessage, partitionKey);
                log.info("Successfully published trade: tradeId={}, jobId={}", 
                    request.getTradeId(), finalJobId);
            } else {
                throw new IllegalStateException(
                    "No messaging system enabled. Enable either Kafka or Solace.");
            }
            
            return finalJobId;
            
        } catch (Exception e) {
            log.error("Error publishing trade to queue: tradeId={}, jobId={}", 
                request.getTradeId(), jobId, e);
            throw new RuntimeException("Failed to publish trade to queue", e);
        }
    }
}

