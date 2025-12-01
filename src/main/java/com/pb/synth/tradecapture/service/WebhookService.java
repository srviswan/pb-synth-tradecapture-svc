package com.pb.synth.tradecapture.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pb.synth.tradecapture.model.AsyncJobStatus;
import com.pb.synth.tradecapture.model.TradeCaptureResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Service for sending webhook callbacks when trade processing completes.
 * Webhooks are required for all async operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookService {
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    @Value("${webhook.timeout-ms:30000}")
    private int timeoutMs;
    
    @Value("${webhook.max-retries:3}")
    private int maxRetries;
    
    /**
     * Send webhook callback for job completion.
     * 
     * @param callbackUrl The webhook URL
     * @param jobStatus The job status
     * @param tradeResponse The trade processing response (if available)
     */
    @Async
    public CompletableFuture<Void> sendWebhook(String callbackUrl, AsyncJobStatus jobStatus, 
                                               TradeCaptureResponse tradeResponse) {
        if (callbackUrl == null || callbackUrl.trim().isEmpty()) {
            log.warn("No callback URL provided, skipping webhook");
            return CompletableFuture.completedFuture(null);
        }
        
        try {
            // Build webhook payload
            Map<String, Object> payload = new HashMap<>();
            payload.put("jobId", jobStatus.getJobId());
            payload.put("status", jobStatus.getStatus().name());
            payload.put("progress", jobStatus.getProgress());
            payload.put("message", jobStatus.getMessage());
            payload.put("createdAt", jobStatus.getCreatedAt());
            payload.put("updatedAt", jobStatus.getUpdatedAt());
            
            if (tradeResponse != null) {
                payload.put("tradeId", tradeResponse.getTradeId());
                payload.put("tradeStatus", tradeResponse.getStatus());
                payload.put("swapBlotter", tradeResponse.getSwapBlotter());
            }
            
            if (jobStatus.getError() != null) {
                payload.put("error", Map.of(
                    "code", jobStatus.getError().getCode(),
                    "message", jobStatus.getError().getMessage()
                ));
            }
            
            // Send webhook with retry
            sendWebhookWithRetry(callbackUrl, payload, 0);
            
            log.info("Webhook sent successfully: callbackUrl={}, jobId={}, status={}", 
                callbackUrl, jobStatus.getJobId(), jobStatus.getStatus());
            
        } catch (Exception e) {
            log.error("Error sending webhook: callbackUrl={}, jobId={}", 
                callbackUrl, jobStatus.getJobId(), e);
            // Don't throw - webhook failure shouldn't fail the job
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Send webhook with retry logic.
     */
    private void sendWebhookWithRetry(String callbackUrl, Map<String, Object> payload, int attempt) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            String jsonPayload = objectMapper.writeValueAsString(payload);
            HttpEntity<String> entity = new HttpEntity<>(jsonPayload, headers);
            
            ResponseEntity<String> response = restTemplate.postForEntity(
                callbackUrl, entity, String.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                log.debug("Webhook sent successfully: callbackUrl={}, attempt={}", 
                    callbackUrl, attempt + 1);
            } else {
                throw new RuntimeException("Webhook returned status: " + response.getStatusCode());
            }
            
        } catch (Exception e) {
            if (attempt < maxRetries - 1) {
                log.warn("Webhook failed, retrying: callbackUrl={}, attempt={}, error={}", 
                    callbackUrl, attempt + 1, e.getMessage());
                
                try {
                    Thread.sleep(1000 * (attempt + 1)); // Exponential backoff
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                
                sendWebhookWithRetry(callbackUrl, payload, attempt + 1);
            } else {
                log.error("Webhook failed after {} attempts: callbackUrl={}", 
                    maxRetries, callbackUrl, e);
                throw new RuntimeException("Failed to send webhook after " + maxRetries + " attempts", e);
            }
        }
    }
}

