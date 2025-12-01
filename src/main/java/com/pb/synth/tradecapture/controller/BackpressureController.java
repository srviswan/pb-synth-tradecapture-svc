package com.pb.synth.tradecapture.controller;

import com.pb.synth.tradecapture.service.backpressure.BackpressureService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST controller for backpressure monitoring and management.
 */
@RestController
@RequestMapping("/api/v1/backpressure")
@RequiredArgsConstructor
public class BackpressureController {
    
    private final BackpressureService backpressureService;
    
    /**
     * Get API-level backpressure status.
     */
    @GetMapping("/api/status")
    public ResponseEntity<BackpressureService.ApiBackpressureStatus> getApiStatus() {
        return ResponseEntity.ok(backpressureService.getApiStatus());
    }
    
    /**
     * Get messaging-level backpressure status.
     */
    @GetMapping("/messaging/status")
    public ResponseEntity<BackpressureService.MessagingBackpressureStatus> getMessagingStatus() {
        return ResponseEntity.ok(backpressureService.getMessagingStatus());
    }
    
    /**
     * Get overall backpressure status.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getOverallStatus() {
        BackpressureService.ApiBackpressureStatus apiStatus = backpressureService.getApiStatus();
        BackpressureService.MessagingBackpressureStatus messagingStatus = backpressureService.getMessagingStatus();
        
        return ResponseEntity.ok(Map.of(
            "api", apiStatus,
            "messaging", messagingStatus,
            "overallUnderPressure", apiStatus.isUnderPressure() || messagingStatus.isUnderPressure(),
            "overallWarning", apiStatus.isWarning() || messagingStatus.isWarning()
        ));
    }
}

