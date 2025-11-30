package com.pb.synth.tradecapture.controller;

import com.pb.synth.tradecapture.config.ConsumerGroupManagementConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for consumer group management and monitoring.
 */
@RestController
@RequestMapping("/api/v1/consumer-groups")
@ConditionalOnProperty(name = "messaging.kafka.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class ConsumerGroupController {

    private final KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;
    private final ConsumerGroupManagementConfig consumerGroupManagement;

    /**
     * Get consumer group status.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getConsumerGroupStatus() {
        Map<String, Object> status = new HashMap<>();
        
        kafkaListenerEndpointRegistry.getAllListenerContainers().forEach(container -> {
            Map<String, Object> containerStatus = new HashMap<>();
            containerStatus.put("listenerId", container.getListenerId());
            containerStatus.put("running", container.isRunning());
            containerStatus.put("active", container.isRunning());
            
            if (container.isRunning()) {
                long lag = consumerGroupManagement.getConsumerLag(container.getListenerId());
                long partitions = consumerGroupManagement.getAssignedPartitions(container.getListenerId());
                boolean healthy = consumerGroupManagement.isConsumerGroupHealthy(container.getListenerId());
                
                containerStatus.put("lag", lag);
                containerStatus.put("assignedPartitions", partitions);
                containerStatus.put("healthy", healthy);
            }
            
            status.put(container.getListenerId(), containerStatus);
        });
        
        return ResponseEntity.ok(status);
    }

    /**
     * Get consumer lag for a specific listener.
     */
    @GetMapping("/{listenerId}/lag")
    public ResponseEntity<Map<String, Object>> getConsumerLag(
            @PathVariable String listenerId) {
        
        long lag = consumerGroupManagement.getConsumerLag(listenerId);
        long partitions = consumerGroupManagement.getAssignedPartitions(listenerId);
        boolean healthy = consumerGroupManagement.isConsumerGroupHealthy(listenerId);
        
        return ResponseEntity.ok(Map.of(
            "listenerId", listenerId,
            "lag", lag,
            "assignedPartitions", partitions,
            "healthy", healthy
        ));
    }

    /**
     * Start a consumer group.
     */
    @PostMapping("/{listenerId}/start")
    public ResponseEntity<Map<String, Object>> startConsumerGroup(
            @PathVariable String listenerId) {
        
        MessageListenerContainer container = kafkaListenerEndpointRegistry.getListenerContainer(listenerId);
        if (container == null) {
            return ResponseEntity.notFound().build();
        }
        
        if (!container.isRunning()) {
            container.start();
            log.info("Started consumer group: {}", listenerId);
        }
        
        return ResponseEntity.ok(Map.of(
            "listenerId", listenerId,
            "status", "STARTED",
            "running", container.isRunning()
        ));
    }

    /**
     * Stop a consumer group.
     */
    @PostMapping("/{listenerId}/stop")
    public ResponseEntity<Map<String, Object>> stopConsumerGroup(
            @PathVariable String listenerId) {
        
        MessageListenerContainer container = kafkaListenerEndpointRegistry.getListenerContainer(listenerId);
        if (container == null) {
            return ResponseEntity.notFound().build();
        }
        
        if (container.isRunning()) {
            container.stop();
            log.info("Stopped consumer group: {}", listenerId);
        }
        
        return ResponseEntity.ok(Map.of(
            "listenerId", listenerId,
            "status", "STOPPED",
            "running", container.isRunning()
        ));
    }

    /**
     * Pause a consumer group.
     */
    @PostMapping("/{listenerId}/pause")
    public ResponseEntity<Map<String, Object>> pauseConsumerGroup(
            @PathVariable String listenerId) {
        
        MessageListenerContainer container = kafkaListenerEndpointRegistry.getListenerContainer(listenerId);
        if (container == null) {
            return ResponseEntity.notFound().build();
        }
        
        if (container.isRunning()) {
            container.pause();
            log.info("Paused consumer group: {}", listenerId);
        }
        
        return ResponseEntity.ok(Map.of(
            "listenerId", listenerId,
            "status", "PAUSED"
        ));
    }

    /**
     * Resume a consumer group.
     */
    @PostMapping("/{listenerId}/resume")
    public ResponseEntity<Map<String, Object>> resumeConsumerGroup(
            @PathVariable String listenerId) {
        
        MessageListenerContainer container = kafkaListenerEndpointRegistry.getListenerContainer(listenerId);
        if (container == null) {
            return ResponseEntity.notFound().build();
        }
        
        // Check if container is running but paused (via reflection or try-catch)
        try {
            container.resume();
            log.info("Resumed consumer group: {}", listenerId);
        } catch (Exception e) {
            log.warn("Could not resume consumer group {}: {}", listenerId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                "listenerId", listenerId,
                "status", "NOT_PAUSED",
                "message", "Consumer group is not paused or cannot be resumed"
            ));
        }
        
        return ResponseEntity.ok(Map.of(
            "listenerId", listenerId,
            "status", "RESUMED"
        ));
    }
}

