package com.pb.synth.tradecapture.messaging;

import com.pb.synth.tradecapture.model.AsyncJobStatus;
import com.pb.synth.tradecapture.model.TradeCaptureRequest;
import com.pb.synth.tradecapture.model.TradeCaptureResponse;
import com.pb.synth.tradecapture.service.JobStatusService;
import com.pb.synth.tradecapture.service.TradeCaptureService;
import com.pb.synth.tradecapture.service.WebhookService;
import com.pb.synth.tradecapture.service.backpressure.BackpressureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Processes incoming trade messages from queues.
 * This component is called by message consumers (Kafka, Solace, etc.)
 * to process trade capture requests.
 * Handles job status tracking and webhook callbacks for API-initiated trades.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TradeMessageProcessor {
    
    private final TradeCaptureService tradeCaptureService;
    private final JobStatusService jobStatusService;
    private final WebhookService webhookService;
    private final BackpressureService backpressureService;
    
    /**
     * Process a trade capture request from a message queue.
     * 
     * @param request The trade capture request
     * @param metadata Optional metadata map (from protobuf message)
     * @return The processing response
     */
    public TradeCaptureResponse processMessage(TradeCaptureRequest request, 
                                              java.util.Map<String, String> metadata) {
        log.info("Processing trade message: tradeId={}, partitionKey={}", 
            request.getTradeId(), request.getPartitionKey());
        
        // Check backpressure before processing
        if (!backpressureService.canProcessMessage()) {
            log.warn("Messaging backpressure: Rejecting message tradeId={}, partitionKey={}", 
                request.getTradeId(), request.getPartitionKey());
            throw new RuntimeException("Service is under backpressure, message rejected");
        }
        
        // Increment processing queue
        backpressureService.incrementProcessingQueue();
        
        // Extract job metadata if this is an API-initiated trade
        String jobId = metadata != null ? metadata.get("job_id") : null;
        String callbackUrl = metadata != null ? metadata.get("callback_url") : null;
        String sourceApi = metadata != null ? metadata.get("source_api") : null;
        
        try {
            // Update job status to PROCESSING if job exists
            if (jobId != null) {
                jobStatusService.updateJobStatus(jobId, AsyncJobStatus.JobStatus.PROCESSING, 
                    50, "Processing trade: " + request.getTradeId());
            }
            
            // Process the trade
            TradeCaptureResponse response = tradeCaptureService.processTrade(request);
            
            // Update job status to COMPLETED/FAILED if job exists
            if (jobId != null) {
                AsyncJobStatus.JobStatus finalStatus = "SUCCESS".equals(response.getStatus()) 
                    ? AsyncJobStatus.JobStatus.COMPLETED 
                    : AsyncJobStatus.JobStatus.FAILED;
                
                jobStatusService.updateJobStatus(jobId, finalStatus, 100, 
                    "Trade processing " + finalStatus.name().toLowerCase(),
                    response, response.getError());
                
                // Send webhook callback if callback URL is provided
                if (callbackUrl != null && !callbackUrl.isEmpty()) {
                    try {
                        AsyncJobStatus jobStatus = jobStatusService.getJobStatus(jobId);
                        webhookService.sendWebhook(callbackUrl, jobStatus, response);
                    } catch (Exception e) {
                        log.error("Error sending webhook callback: jobId={}, callbackUrl={}", 
                            jobId, callbackUrl, e);
                        // Don't fail the job if webhook fails
                    }
                }
            }
            
            log.info("Successfully processed trade: tradeId={}, status={}, jobId={}", 
                request.getTradeId(), response.getStatus(), jobId);
            return response;
            
        } catch (Exception e) {
            log.error("Error processing trade message: tradeId={}, jobId={}", 
                request.getTradeId(), jobId, e);
            
            // Update job status to FAILED if job exists
            if (jobId != null) {
                jobStatusService.updateJobStatus(jobId, AsyncJobStatus.JobStatus.FAILED, 100,
                    "Trade processing failed: " + e.getMessage(),
                    null, com.pb.synth.tradecapture.model.ErrorDetail.builder()
                        .code("PROCESSING_ERROR")
                        .message(e.getMessage())
                        .timestamp(java.time.ZonedDateTime.now())
                        .build());
                
                // Send webhook callback for failure
                if (callbackUrl != null && !callbackUrl.isEmpty()) {
                    try {
                        AsyncJobStatus jobStatus = jobStatusService.getJobStatus(jobId);
                        webhookService.sendWebhook(callbackUrl, jobStatus, null);
                    } catch (Exception webhookError) {
                        log.error("Error sending webhook callback for failed job: jobId={}", 
                            jobId, webhookError);
                    }
                }
            }
            
            throw new RuntimeException("Failed to process trade message", e);
        } finally {
            // Always decrement processing queue when done (success or failure)
            backpressureService.decrementProcessingQueue();
        }
    }
    
    /**
     * Process a trade capture request from a message queue (backward compatibility).
     * 
     * @param request The trade capture request
     * @return The processing response
     */
    public TradeCaptureResponse processMessage(TradeCaptureRequest request) {
        return processMessage(request, null);
    }
}

