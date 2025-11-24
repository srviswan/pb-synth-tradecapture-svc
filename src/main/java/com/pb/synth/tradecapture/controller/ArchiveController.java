package com.pb.synth.tradecapture.controller;

import com.pb.synth.tradecapture.service.ArchiveService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.ZonedDateTime;

/**
 * REST controller for archive operations.
 */
@RestController
@RequestMapping("/api/v1/archive")
@RequiredArgsConstructor
@Slf4j
public class ArchiveController {

    private final ArchiveService archiveService;

    /**
     * Archive a specific trade by trade ID.
     */
    @PostMapping("/trades/{tradeId}")
    public ResponseEntity<Void> archiveTrade(@PathVariable String tradeId) {
        archiveService.archiveTrade(tradeId);
        return ResponseEntity.accepted().build();
    }

    /**
     * Archive records by date range.
     */
    @PostMapping("/date-range")
    public ResponseEntity<ArchiveService.ArchiveResult> archiveByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime endDate) {
        
        ArchiveService.ArchiveResult result = archiveService.archiveByDateRange(startDate, endDate);
        return ResponseEntity.ok(result);
    }

    /**
     * Archive expired idempotency records.
     */
    @PostMapping("/expired-idempotency")
    public ResponseEntity<Void> archiveExpiredIdempotencyRecords() {
        archiveService.archiveExpiredIdempotencyRecords();
        return ResponseEntity.accepted().build();
    }
}

