package com.pb.synth.tradecapture.messaging;

/**
 * Abstraction for consuming trade messages from various messaging systems.
 * Implementations handle message consumption from Kafka, Solace, RabbitMQ, etc.
 */
public interface TradeMessageConsumer {
    
    /**
     * Start consuming messages from the configured queue/topic.
     * Messages are processed asynchronously and passed to the processor.
     */
    void start();
    
    /**
     * Stop consuming messages and clean up resources.
     */
    void stop();
    
    /**
     * Check if the consumer is currently running.
     */
    boolean isRunning();
}

