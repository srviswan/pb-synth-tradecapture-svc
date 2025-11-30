package com.pb.synth.tradecapture.service;

import com.pb.synth.tradecapture.model.SwapBlotter;
import com.pb.synth.tradecapture.model.WorkflowStatus;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Client for Approval Workflow Service with circuit breaker and retry.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApprovalWorkflowServiceClient {

    private final RestTemplate restTemplate;

    @Value("${services.approval-workflow.url}")
    private String baseUrl;

    @Value("${services.approval-workflow.timeout:5000}")
    private int timeout;

    @Value("${services.approval-workflow.mock:false}")
    private boolean mockMode;

    /**
     * Submit trade for approval (async).
     * Returns APPROVED if approved, PENDING_APPROVAL if pending, REJECTED if rejected.
     */
    @CircuitBreaker(name = "approvalWorkflowService", fallbackMethod = "submitForApprovalFallback")
    @Retry(name = "approvalWorkflowService")
    @TimeLimiter(name = "approvalWorkflowService")
    public CompletableFuture<WorkflowStatus> submitForApprovalAsync(SwapBlotter swapBlotter) {
        return CompletableFuture.supplyAsync(() -> {
            // Return mock approval if mock mode is enabled
            if (mockMode) {
                log.info("Mock ApprovalWorkflowService: Submitting trade {} for approval", swapBlotter.getTradeId());
                // In mock mode, auto-approve for testing
                log.debug("Mock ApprovalWorkflowService: Auto-approving trade {}", swapBlotter.getTradeId());
                return WorkflowStatus.APPROVED;
            }

            try {
                String url = baseUrl + "/api/v1/approvals/submit";
                
                // Build approval request
                Map<String, Object> approvalRequest = new HashMap<>();
                approvalRequest.put("tradeId", swapBlotter.getTradeId());
                approvalRequest.put("partitionKey", swapBlotter.getPartitionKey());
                approvalRequest.put("workflowStatus", swapBlotter.getWorkflowStatus());
                approvalRequest.put("enrichmentStatus", swapBlotter.getEnrichmentStatus());
                approvalRequest.put("tradeLots", swapBlotter.getTradeLots());
                
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(approvalRequest, headers);
                
                @SuppressWarnings({"unchecked", "null"})
                Map<String, Object> response = restTemplate.exchange(
                    url, HttpMethod.POST, requestEntity, Map.class
                ).getBody();
                
                if (response != null && response.containsKey("status")) {
                    String status = response.get("status").toString();
                    log.info("Approval response for trade {}: {}", swapBlotter.getTradeId(), status);
                    return WorkflowStatus.valueOf(status.toUpperCase());
                }
                
                log.warn("Unexpected approval response format for trade {}", swapBlotter.getTradeId());
                return WorkflowStatus.PENDING_APPROVAL;
                
            } catch (Exception e) {
                log.error("Error submitting trade {} for approval", swapBlotter.getTradeId(), e);
                throw new RuntimeException("Failed to submit for approval", e);
            }
        });
    }

    /**
     * Synchronous version for backward compatibility.
     */
    public WorkflowStatus submitForApproval(SwapBlotter swapBlotter) {
        try {
            return submitForApprovalAsync(swapBlotter).get();
        } catch (Exception e) {
            log.error("Error in synchronous approval submission: {}", swapBlotter.getTradeId(), e);
            try {
                return submitForApprovalFallback(swapBlotter, e).get();
            } catch (Exception ex) {
                log.error("Error in fallback for approval submission", ex);
                return WorkflowStatus.PENDING_APPROVAL;
            }
        }
    }

    /**
     * Check approval status for a trade (async).
     */
    @CircuitBreaker(name = "approvalWorkflowService", fallbackMethod = "checkApprovalStatusFallback")
    @Retry(name = "approvalWorkflowService")
    @TimeLimiter(name = "approvalWorkflowService")
    public CompletableFuture<Optional<WorkflowStatus>> checkApprovalStatusAsync(String tradeId) {
        return CompletableFuture.supplyAsync(() -> {
            // Return mock status if mock mode is enabled
            if (mockMode) {
                log.info("Mock ApprovalWorkflowService: Checking approval status for trade {}", tradeId);
                return Optional.of(WorkflowStatus.APPROVED);
            }

            try {
                String url = baseUrl + "/api/v1/approvals/" + tradeId + "/status";
                
                @SuppressWarnings("unchecked")
                Map<String, Object> response = restTemplate.getForObject(url, Map.class);
                
                if (response != null && response.containsKey("status")) {
                    String status = response.get("status").toString();
                    return Optional.of(WorkflowStatus.valueOf(status.toUpperCase()));
                }
                
                return Optional.empty();
                
            } catch (Exception e) {
                log.error("Error checking approval status for trade {}", tradeId, e);
                throw new RuntimeException("Failed to check approval status", e);
            }
        });
    }

    /**
     * Synchronous version for backward compatibility.
     */
    public Optional<WorkflowStatus> checkApprovalStatus(String tradeId) {
        try {
            return checkApprovalStatusAsync(tradeId).get();
        } catch (Exception e) {
            log.error("Error in synchronous approval status check: {}", tradeId, e);
            try {
                return checkApprovalStatusFallback(tradeId, e).get();
            } catch (Exception ex) {
                log.error("Error in fallback for approval status check", ex);
                return Optional.empty();
            }
        }
    }

    /**
     * Fallback method when circuit breaker is open or service fails.
     * Must be public for Resilience4j to find it.
     */
    @SuppressWarnings("unused")
    public CompletableFuture<WorkflowStatus> submitForApprovalFallback(SwapBlotter swapBlotter, Throwable e) {
        log.warn("Using fallback for approval submission: {}, error: {}", 
            swapBlotter.getTradeId(), e != null ? e.getMessage() : "unknown");
        // Default to PENDING_APPROVAL on failure
        return CompletableFuture.completedFuture(WorkflowStatus.PENDING_APPROVAL);
    }

    /**
     * Fallback method for checking approval status.
     * Must be public for Resilience4j to find it.
     */
    @SuppressWarnings("unused")
    public CompletableFuture<Optional<WorkflowStatus>> checkApprovalStatusFallback(String tradeId, Throwable e) {
        log.warn("Using fallback for approval status check: {}, error: {}", 
            tradeId, e != null ? e.getMessage() : "unknown");
        return CompletableFuture.completedFuture(Optional.empty());
    }
}

