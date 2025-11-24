package com.pb.synth.tradecapture.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZonedDateTime;
import java.util.Map;

/**
 * Health check controller.
 */
@RestController
@RequestMapping("/api/v1/health")
@Slf4j
public class HealthController {

    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "timestamp", ZonedDateTime.now(),
            "service", "pb-synth-tradecapture-svc"
        ));
    }
}

