package com.pb.synth.tradecapture.controller;

import com.pb.synth.tradecapture.model.TradeCaptureRequest;
import com.pb.synth.tradecapture.service.JobStatusService;
import com.pb.synth.tradecapture.service.QuickValidationService;
import com.pb.synth.tradecapture.service.TradePublishingService;
import com.pb.synth.tradecapture.service.parser.TradeFileParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for bulk trade upload via file.
 * Supports CSV, JSON, JSONL, and Excel (XLSX) formats.
 * Maximum 5,000 trades per file.
 */
@RestController
@RequestMapping("/api/v1/trades")
@RequiredArgsConstructor
@Slf4j
public class FileUploadController {
    
    private final TradeFileParser tradeFileParser;
    private final QuickValidationService quickValidationService;
    private final TradePublishingService tradePublishingService;
    private final JobStatusService jobStatusService;
    
    private static final int MAX_TRADES = 5000;
    
    /**
     * Upload a file containing multiple trades for bulk processing.
     * 
     * @param file The uploaded file (CSV, JSON, JSONL, or Excel)
     * @param callbackUrl Required webhook callback URL for completion notification
     * @return 202 Accepted with batch job ID and summary
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadTrades(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-Callback-Url", required = true) String callbackUrl) {
        
        log.info("Processing file upload: filename={}, size={}", 
            file.getOriginalFilename(), file.getSize());
        
        // Validate file
        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "File is empty"));
        }
        
        try {
            // Parse file
            List<TradeCaptureRequest> trades = tradeFileParser.parseFile(file);
            
            if (trades.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "No trades found in file"));
            }
            
            if (trades.size() > MAX_TRADES) {
                return ResponseEntity.badRequest()
                    .body(Map.of(
                        "error", "File exceeds maximum of " + MAX_TRADES + " trades",
                        "tradesFound", trades.size()
                    ));
            }
            
            // Batch validation
            List<Map<String, Object>> invalidTrades = new ArrayList<>();
            List<TradeCaptureRequest> validTrades = new ArrayList<>();
            
            for (int i = 0; i < trades.size(); i++) {
                TradeCaptureRequest trade = trades.get(i);
                QuickValidationService.ValidationResult validation = 
                    quickValidationService.validateQuick(trade);
                
                if (validation.isPassed()) {
                    validTrades.add(trade);
                } else {
                    invalidTrades.add(Map.of(
                        "row", i + 1,
                        "tradeId", trade.getTradeId() != null ? trade.getTradeId() : "N/A",
                        "errors", validation.getErrors().stream()
                            .map(error -> Map.of(
                                "field", error.getField(),
                                "message", error.getMessage()
                            ))
                            .collect(Collectors.toList())
                    ));
                }
            }
            
            // Create batch job
            String batchJobId = "batch-" + UUID.randomUUID().toString();
            jobStatusService.createJob(batchJobId, "BATCH_UPLOAD", "FILE_UPLOAD");
            
            // Publish valid trades to queue
            int publishedCount = 0;
            for (TradeCaptureRequest trade : validTrades) {
                String tradeJobId = UUID.randomUUID().toString();
                jobStatusService.createJob(tradeJobId, trade.getTradeId(), "FILE_UPLOAD");
                tradePublishingService.publishTrade(trade, tradeJobId, "FILE_UPLOAD", callbackUrl);
                publishedCount++;
            }
            
            // Update batch job status
            jobStatusService.updateJobStatus(batchJobId, 
                com.pb.synth.tradecapture.model.AsyncJobStatus.JobStatus.PROCESSING,
                (publishedCount * 100) / trades.size(),
                String.format("Published %d of %d trades", publishedCount, trades.size()));
            
            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("jobId", batchJobId);
            response.put("status", "ACCEPTED");
            response.put("message", "File uploaded and trades submitted for processing");
            response.put("summary", Map.of(
                "totalTrades", trades.size(),
                "validTrades", validTrades.size(),
                "invalidTrades", invalidTrades.size(),
                "publishedTrades", publishedCount
            ));
            
            if (!invalidTrades.isEmpty()) {
                response.put("invalidDetails", invalidTrades);
            }
            
            response.put("statusUrl", "/api/v1/trades/jobs/" + batchJobId + "/status");
            
            log.info("File upload completed: batchJobId={}, totalTrades={}, validTrades={}, invalidTrades={}", 
                batchJobId, trades.size(), validTrades.size(), invalidTrades.size());
            
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
            
        } catch (IllegalArgumentException e) {
            log.warn("File upload validation failed: {}", e.getMessage());
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error processing file upload", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to process file: " + e.getMessage()));
        }
    }
}

