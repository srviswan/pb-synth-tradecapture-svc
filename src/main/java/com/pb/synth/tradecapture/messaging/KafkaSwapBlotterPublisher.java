package com.pb.synth.tradecapture.messaging;

import com.pb.synth.tradecapture.model.SwapBlotter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Kafka publisher for SwapBlotter.
 * This is a placeholder implementation - actual Kafka integration would be implemented here.
 */
@Component
@ConditionalOnProperty(name = "messaging.kafka.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class KafkaSwapBlotterPublisher implements SwapBlotterPublisher {

    @Value("${messaging.kafka.topics.output:trade-capture-blotter}")
    private String outputTopic;

    @Override
    public void publish(SwapBlotter swapBlotter) {
        log.info("Publishing SwapBlotter to Kafka topic {}: {}", outputTopic, swapBlotter.getTradeId());
        // TODO: Implement actual Kafka publishing
        // - Serialize SwapBlotter to JSON or protobuf
        // - Publish to Kafka topic
        // - Use partition key for partitioning
    }
}

