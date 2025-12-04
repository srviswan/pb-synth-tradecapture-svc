package com.pb.synth.tradecapture.messaging;

import com.pb.synth.tradecapture.proto.TradeCaptureProto;

/**
 * Interface for publishing trade input messages to various messaging systems.
 * Abstracts the underlying messaging implementation (Kafka, Solace, etc.)
 * from the service layer.
 * 
 * Implementations handle:
 * - Message serialization (protobuf)
 * - Partition key routing
 * - Error handling and retries
 * - Connection management
 */
public interface TradeInputPublisher {
    
    /**
     * Publish a trade capture message to the input queue/topic.
     * 
     * @param protoMessage The protobuf trade capture message
     * @param partitionKey The partition key for routing (e.g., "ACC-001_BOOK-001_SEC-001")
     * @throws RuntimeException if publishing fails
     */
    void publish(TradeCaptureProto.TradeCaptureMessage protoMessage, String partitionKey);
    
    /**
     * Check if this publisher is available and ready to publish.
     * 
     * @return true if ready, false otherwise
     */
    default boolean isAvailable() {
        return true;
    }
}

