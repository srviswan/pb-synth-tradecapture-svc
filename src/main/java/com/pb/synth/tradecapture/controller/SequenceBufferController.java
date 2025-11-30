package com.pb.synth.tradecapture.controller;

import com.pb.synth.tradecapture.service.OutOfOrderMessageBuffer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for sequence buffer management and monitoring.
 */
@RestController
@RequestMapping("/api/v1/sequence-buffer")
@RequiredArgsConstructor
public class SequenceBufferController {

    private final OutOfOrderMessageBuffer outOfOrderMessageBuffer;

    /**
     * Get buffer status for monitoring.
     */
    @GetMapping("/status")
    public ResponseEntity<OutOfOrderMessageBuffer.BufferStatus> getBufferStatus() {
        return ResponseEntity.ok(outOfOrderMessageBuffer.getBufferStatus());
    }

    /**
     * Clear buffer for a partition (admin operation).
     */
    @DeleteMapping("/partitions/{partitionKey}")
    public ResponseEntity<Void> clearBuffer(@PathVariable String partitionKey) {
        outOfOrderMessageBuffer.clearBuffer(partitionKey);
        return ResponseEntity.noContent().build();
    }
}

