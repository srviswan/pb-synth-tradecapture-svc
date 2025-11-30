package com.pb.synth.tradecapture.service;

import com.pb.synth.tradecapture.model.AsyncJobStatus;
import com.pb.synth.tradecapture.model.TradeCaptureRequest;
import com.pb.synth.tradecapture.model.TradeCaptureResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing async trade processing jobs.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AsyncTradeProcessingService {

    private final TradeCaptureService tradeCaptureService;
    
    // In-memory job store (in production, use Redis or database)
    private final Map<String, AsyncJobStatus> jobStore = new ConcurrentHashMap<>();
    
    /**
     * Submit a trade for async processing.
     * Returns immediately with a job ID.
     */
    public String submitAsyncTrade(TradeCaptureRequest request) {
        String jobId = UUID.randomUUID().toString();
        
        AsyncJobStatus jobStatus = AsyncJobStatus.builder()
            .jobId(jobId)
            .status(AsyncJobStatus.JobStatus.PENDING)
            .progress(0)
            .message("Trade submitted for processing")
            .createdAt(ZonedDateTime.now())
            .build();
        
        jobStore.put(jobId, jobStatus);
        
        // Process asynchronously
        processTradeAsync(jobId, request);
        
        log.info("Submitted trade for async processing: tradeId={}, jobId={}", 
            request.getTradeId(), jobId);
        
        return jobId;
    }
    
    /**
     * Process trade asynchronously.
     */
    @Async("partitionProcessingExecutor")
    public CompletableFuture<Void> processTradeAsync(String jobId, TradeCaptureRequest request) {
        try {
            // Update status to PROCESSING
            updateJobStatus(jobId, AsyncJobStatus.JobStatus.PROCESSING, 10, 
                "Processing trade: " + request.getTradeId());
            
            // Process the trade
            TradeCaptureResponse response = tradeCaptureService.processTrade(request);
            
            // Update status to COMPLETED
            AsyncJobStatus.JobStatus finalStatus = "SUCCESS".equals(response.getStatus()) 
                ? AsyncJobStatus.JobStatus.COMPLETED 
                : AsyncJobStatus.JobStatus.FAILED;
            
            updateJobStatus(jobId, finalStatus, 100, 
                "Trade processing " + finalStatus.name().toLowerCase(),
                response, response.getError());
            
            log.info("Async trade processing completed: jobId={}, tradeId={}, status={}", 
                jobId, request.getTradeId(), finalStatus);
            
        } catch (Exception e) {
            log.error("Error processing async trade: jobId={}, tradeId={}", 
                jobId, request.getTradeId(), e);
            
            updateJobStatus(jobId, AsyncJobStatus.JobStatus.FAILED, 100,
                "Trade processing failed: " + e.getMessage(),
                null, com.pb.synth.tradecapture.model.ErrorDetail.builder()
                    .code("PROCESSING_ERROR")
                    .message(e.getMessage())
                    .timestamp(ZonedDateTime.now())
                    .build());
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Get job status by job ID.
     */
    public AsyncJobStatus getJobStatus(String jobId) {
        AsyncJobStatus status = jobStore.get(jobId);
        if (status == null) {
            throw new IllegalArgumentException("Job not found: " + jobId);
        }
        return status;
    }
    
    /**
     * Update job status.
     */
    private void updateJobStatus(String jobId, AsyncJobStatus.JobStatus status, 
                                 int progress, String message) {
        updateJobStatus(jobId, status, progress, message, null, null);
    }
    
    /**
     * Update job status with result or error.
     */
    private void updateJobStatus(String jobId, AsyncJobStatus.JobStatus status, 
                                 int progress, String message,
                                 TradeCaptureResponse result, 
                                 com.pb.synth.tradecapture.model.ErrorDetail error) {
        AsyncJobStatus jobStatus = jobStore.get(jobId);
        if (jobStatus != null) {
            jobStatus.setStatus(status);
            jobStatus.setProgress(progress);
            jobStatus.setMessage(message);
            jobStatus.setResult(result);
            jobStatus.setError(error);
            jobStatus.setUpdatedAt(ZonedDateTime.now());
            
            if (status == AsyncJobStatus.JobStatus.COMPLETED || 
                status == AsyncJobStatus.JobStatus.FAILED) {
                // Estimate completion time for completed/failed jobs
                jobStatus.setEstimatedCompletionTime(ZonedDateTime.now());
            }
        }
    }
    
    /**
     * Cancel a job (if still pending or processing).
     */
    public boolean cancelJob(String jobId) {
        AsyncJobStatus jobStatus = jobStore.get(jobId);
        if (jobStatus != null && 
            (jobStatus.getStatus() == AsyncJobStatus.JobStatus.PENDING ||
             jobStatus.getStatus() == AsyncJobStatus.JobStatus.PROCESSING)) {
            
            jobStatus.setStatus(AsyncJobStatus.JobStatus.CANCELLED);
            jobStatus.setMessage("Job cancelled by user");
            jobStatus.setUpdatedAt(ZonedDateTime.now());
            
            log.info("Job cancelled: jobId={}", jobId);
            return true;
        }
        return false;
    }
}


