package com.pb.synth.tradecapture.controller;

import com.pb.synth.tradecapture.service.ratelimit.RateLimitService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for rate limit management and monitoring.
 * Priority 5.1: Rate Limiting
 */
@RestController
@RequestMapping("/api/v1/rate-limit")
@RequiredArgsConstructor
public class RateLimitController {
    
    private final RateLimitService rateLimitService;
    
    /**
     * Get rate limit status for a partition.
     */
    @GetMapping("/status/{partitionKey}")
    public ResponseEntity<RateLimitService.RateLimitStatus> getStatus(
            @PathVariable("partitionKey") String partitionKey) {
        RateLimitService.RateLimitStatus status = rateLimitService.getStatus(partitionKey);
        return ResponseEntity.ok(status);
    }
}



