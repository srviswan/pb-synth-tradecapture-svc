package com.pb.synth.tradecapture.service;

import com.pb.synth.tradecapture.config.MetricsConfig;
import com.pb.synth.tradecapture.model.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Main service for trade capture orchestration.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TradeCaptureService {

    private final IdempotencyService idempotencyService;
    private final EnrichmentService enrichmentService;
    private final RulesEngine rulesEngine;
    private final ValidationService validationService;
    private final StateManagementService stateManagementService;
    private final SwapBlotterService swapBlotterService;
    private final SwapBlotterPublisherService swapBlotterPublisherService;
    private final PartitionLockService partitionLockService;
    private final ApprovalWorkflowServiceClient approvalWorkflowServiceClient;
    private final SequenceNumberService sequenceNumberService;
    private final OutOfOrderMessageBuffer outOfOrderMessageBuffer;
    private final MetricsConfig metricsConfig;
    private final MeterRegistry meterRegistry;
    private final com.pb.synth.tradecapture.service.ratelimit.RateLimitService rateLimitService;

    /**
     * Process trade capture request with partition locking and sequence validation.
     * 
     * Transaction boundary optimization:
     * - Read-only operations (enrichment, validation, rules) are outside transaction
     * - Only critical write operations are in transactions (with REQUIRES_NEW for isolation)
     * - This reduces lock hold time and deadlock probability
     */
    public TradeCaptureResponse processTrade(TradeCaptureRequest request) {
        // Add to MDC for logging
        MDC.put("tradeId", request.getTradeId());
        MDC.put("partitionKey", request.getPartitionKey());
        
        // Record metrics
        metricsConfig.getTradesProcessedCounter().increment();
        Timer.Sample sample = Timer.start(meterRegistry);
        
        long startTime = System.currentTimeMillis();
        String partitionKey = request.getPartitionKey();
        boolean lockAcquired = false;
        
        try {
            // 1. Acquire partition lock (distributed lock for cross-instance coordination)
            // Lock acquisition is outside transaction to minimize lock hold time
            lockAcquired = partitionLockService.acquireLock(partitionKey, 
                Duration.ofMinutes(5), Duration.ofSeconds(30));
            
            if (!lockAcquired) {
                log.error("Failed to acquire lock for partition: {}, tradeId: {}", 
                    partitionKey, request.getTradeId());
                return TradeCaptureResponse.builder()
                    .tradeId(request.getTradeId())
                    .status("FAILED")
                    .error(ErrorDetail.builder()
                        .code("LOCK_ACQUISITION_FAILED")
                        .message("Failed to acquire partition lock")
                        .timestamp(ZonedDateTime.now())
                        .build())
                    .build();
            }
            
            // 1.5. Check rate limit (Priority 5.1)
            if (!rateLimitService.isAllowed(partitionKey)) {
                log.warn("Rate limit exceeded for partition: {}, tradeId: {}", partitionKey, request.getTradeId());
                metricsConfig.getTradesFailedCounter().increment();
                return TradeCaptureResponse.builder()
                    .tradeId(request.getTradeId())
                    .status("FAILED")
                    .error(ErrorDetail.builder()
                        .code("RATE_LIMIT_EXCEEDED")
                        .message("Rate limit exceeded for partition")
                        .timestamp(ZonedDateTime.now())
                        .build())
                    .build();
            }
            
            // 2. Validate sequence number (if provided in message)
            // Priority 4.1: Extract and validate sequence numbers
            if (request.getSequenceNumber() != null) {
                // Use out-of-order message buffer for sequence validation and buffering
                OutOfOrderMessageBuffer.BufferResult bufferResult = 
                    outOfOrderMessageBuffer.processWithSequenceValidation(request);
                
                if (!bufferResult.isShouldProcess()) {
                    String reason = bufferResult.getReason();
                    
                    // If validation is disabled, continue processing
                    if ("VALIDATION_DISABLED".equals(reason)) {
                        log.debug("Sequence validation disabled - continuing with normal processing: tradeId={}", request.getTradeId());
                        // Continue with normal processing
                    } else {
                        log.warn("Message not processed due to sequence validation: tradeId={}, reason={}, expected={}, received={}", 
                            request.getTradeId(), reason, 
                            bufferResult.getExpectedSequence(), bufferResult.getReceivedSequence());
                        
                        if ("BUFFERED".equals(reason) || "BUFFERED_EARLIER".equals(reason)) {
                            // Message is buffered - return pending response
                            return TradeCaptureResponse.builder()
                                .tradeId(request.getTradeId())
                                .status("BUFFERED")
                                .build();
                        } else if ("GAP_TOO_LARGE".equals(reason) || "OUT_OF_ORDER_TOO_OLD".equals(reason)) {
                            // Message rejected - already sent to DLQ by buffer service
                            return TradeCaptureResponse.builder()
                                .tradeId(request.getTradeId())
                                .status("REJECTED")
                                .error(ErrorDetail.builder()
                                    .code("SEQUENCE_VALIDATION_FAILED")
                                    .message(String.format("Sequence validation failed: %s (expected: %d, received: %d)", 
                                        reason, bufferResult.getExpectedSequence(), bufferResult.getReceivedSequence()))
                                    .timestamp(ZonedDateTime.now())
                                    .build())
                                .build();
                        }
                    }
                }
                // If shouldProcess is true (including VALIDATION_DISABLED, NATURAL_GAP, BACKDATED_BOOKING, OUTSIDE_TIME_WINDOW), continue with normal processing
            } else {
                // No sequence number provided - use SequenceNumberService to validate/update
                // This handles the case where sequence number is managed internally
                // (e.g., for manual entry or when sequence number is not in message)
                // For now, we'll skip sequence validation if not provided
                log.debug("No sequence number provided for trade: {}", request.getTradeId());
            }
            
            // 3. Check idempotency (double-check within lock)
            // This is a read operation - no transaction needed
            Optional<SwapBlotter> cached = idempotencyService.checkDuplicate(request);
            if (cached.isPresent()) {
                log.info("Returning cached SwapBlotter for tradeId: {}", request.getTradeId());
                metricsConfig.getTradesDuplicateCounter().increment();
                sample.stop(metricsConfig.getTradeProcessingTimer());
                return TradeCaptureResponse.builder()
                    .tradeId(request.getTradeId())
                    .status("DUPLICATE")
                    .swapBlotter(cached.get())
                    .build();
            }
            
            // 4. Create idempotency record (uses REQUIRES_NEW - isolated transaction)
            idempotencyService.createIdempotencyRecord(request);
            
            // 5. Enrichment (read-only, external service calls - outside transaction)
            EnrichmentService.EnrichmentResult enrichmentResult = enrichmentService.enrich(request);
            
            // 6. Build initial SwapBlotter (in-memory operation - no transaction)
            SwapBlotter swapBlotter = buildInitialSwapBlotter(request, enrichmentResult);
            
            // 7. Apply rules (read-only, in-memory - outside transaction)
            Map<String, Object> tradeData = buildTradeDataMap(request, enrichmentResult);
            swapBlotter = rulesEngine.applyRules(swapBlotter, tradeData);
            
            // 8. Validation (read-only, in-memory - outside transaction)
            validationService.validate(request);
            
            // 9. Approval workflow - send to approval service if needed (external call - outside transaction)
            if (swapBlotter.getWorkflowStatus() == WorkflowStatus.PENDING_APPROVAL) {
                log.info("Trade {} requires approval, submitting to approval workflow service", request.getTradeId());
                WorkflowStatus approvalStatus = approvalWorkflowServiceClient.submitForApproval(swapBlotter);
                
                if (approvalStatus == WorkflowStatus.REJECTED) {
                    log.warn("Trade {} was rejected by approval workflow", request.getTradeId());
                    return TradeCaptureResponse.builder()
                        .tradeId(request.getTradeId())
                        .status("REJECTED")
                        .error(ErrorDetail.builder()
                            .code("TRADE_REJECTED")
                            .message("Trade was rejected by approval workflow")
                            .timestamp(ZonedDateTime.now())
                            .build())
                        .build();
                } else if (approvalStatus == WorkflowStatus.APPROVED) {
                    log.info("Trade {} was approved by approval workflow", request.getTradeId());
                    swapBlotter.setWorkflowStatus(WorkflowStatus.APPROVED);
                } else {
                    // Still pending - return pending response
                    log.info("Trade {} is still pending approval", request.getTradeId());
                    return TradeCaptureResponse.builder()
                        .tradeId(request.getTradeId())
                        .status("PENDING_APPROVAL")
                        .swapBlotter(swapBlotter)
                        .build();
                }
            }
            
            // Only proceed with blotter creation if approved
            if (swapBlotter.getWorkflowStatus() != WorkflowStatus.APPROVED) {
                log.warn("Trade {} workflow status is not APPROVED, cannot proceed", request.getTradeId());
                return TradeCaptureResponse.builder()
                    .tradeId(request.getTradeId())
                    .status("FAILED")
                    .error(ErrorDetail.builder()
                        .code("WORKFLOW_NOT_APPROVED")
                        .message("Trade workflow status is not APPROVED")
                        .timestamp(ZonedDateTime.now())
                        .build())
                    .build();
            }
            
            // 10. State management (read current state - outside transaction, update uses REQUIRES_NEW)
            // Get current state for validation (read-only, uses cache)
            Optional<State> currentStateOpt = stateManagementService.getState(request.getPartitionKey());
            
            // Determine new state: if no current state, start with EXECUTED, otherwise transition to FORMED
            PositionStatusEnum newPositionState;
            if (currentStateOpt.isEmpty()) {
                // New partition - start with EXECUTED
                newPositionState = PositionStatusEnum.EXECUTED;
            } else {
                // Existing partition - transition from EXECUTED to FORMED
                PositionStatusEnum currentState = currentStateOpt.get().getPositionState();
                if (currentState == PositionStatusEnum.EXECUTED) {
                    newPositionState = PositionStatusEnum.FORMED;
                } else if (currentState == PositionStatusEnum.FORMED) {
                    // Already FORMED - keep it as FORMED (no transition needed, just update)
                    newPositionState = PositionStatusEnum.FORMED;
                } else {
                    // If already beyond FORMED (e.g., SETTLED), keep current state
                    newPositionState = currentState;
                }
            }
            
            State newState = State.builder()
                .positionState(newPositionState)
                .build();
            
            // Only validate transition if state is actually changing (in-memory validation)
            if (currentStateOpt.isPresent()) {
                PositionStatusEnum currentState = currentStateOpt.get().getPositionState();
                if (currentState != newPositionState) {
                    // State is changing - validate the transition
                    stateManagementService.validateStateTransition(currentState, newPositionState);
                }
                // If state is not changing, skip validation (e.g., FORMED -> FORMED)
            }
            
            // Update state (uses REQUIRES_NEW - isolated transaction, minimizes lock time)
            stateManagementService.updateState(request.getPartitionKey(), newState);
            swapBlotter.setState(newState);
            
            // 11. Save SwapBlotter (uses REQUIRES_NEW - isolated transaction)
            swapBlotterService.saveSwapBlotter(swapBlotter);
            
            // 12. Update sequence number (derived from tradeDate + tradeTimestamp) - after successful processing
            Long sequenceNumber = request.getSequenceNumber();
            if (sequenceNumber != null) {
                sequenceNumberService.validateAndUpdateSequence(request);
            }
            
            // 13. Update idempotency record (uses REQUIRES_NEW - isolated transaction)
            idempotencyService.markCompleted(request.getIdempotencyKey(), swapBlotter.getTradeId());
            
            // 14. Publish to subscribers (async operation - outside transaction)
            swapBlotterPublisherService.publish(swapBlotter);
            
            long processingTime = System.currentTimeMillis() - startTime;
            swapBlotter.getProcessingMetadata().setProcessingTimeMs(processingTime);
            
            // Record success metrics
            metricsConfig.getTradesSuccessfulCounter().increment();
            sample.stop(metricsConfig.getTradeProcessingTimer());
            
            return TradeCaptureResponse.builder()
                .tradeId(request.getTradeId())
                .status("SUCCESS")
                .swapBlotter(swapBlotter)
                .build();
                
        } catch (Exception e) {
            log.error("Error processing trade: {}", request.getTradeId(), e);
            idempotencyService.markFailed(request.getIdempotencyKey());
            
            // Record failure metrics
            metricsConfig.getTradesFailedCounter().increment();
            sample.stop(metricsConfig.getTradeProcessingTimer());
            
            return TradeCaptureResponse.builder()
                .tradeId(request.getTradeId())
                .status("FAILED")
                .error(ErrorDetail.builder()
                    .code("PROCESSING_ERROR")
                    .message(e.getMessage())
                    .timestamp(ZonedDateTime.now())
                    .build())
                .build();
        } finally {
            // Always release the lock
            if (lockAcquired) {
                partitionLockService.releaseLock(partitionKey);
            }
            // Clean up MDC
            MDC.remove("tradeId");
            MDC.remove("partitionKey");
        }
    }

    private SwapBlotter buildInitialSwapBlotter(TradeCaptureRequest request, EnrichmentService.EnrichmentResult enrichmentResult) {
        return SwapBlotter.builder()
            .tradeId(request.getTradeId())
            .partitionKey(request.getPartitionKey())
            .tradeLots(request.getTradeLots())
            .enrichmentStatus(enrichmentResult.getStatus())
            .workflowStatus(WorkflowStatus.PENDING_APPROVAL) // Default, can be overridden by rules
            .processingMetadata(ProcessingMetadata.builder()
                .processedAt(ZonedDateTime.now())
                .enrichmentSources(List.of("SecurityMasterService", "AccountService"))
                .build())
            .version(0L)
            .build();
    }

    private Map<String, Object> buildTradeDataMap(TradeCaptureRequest request, EnrichmentService.EnrichmentResult enrichmentResult) {
        return Map.of(
            "tradeId", request.getTradeId(),
            "source", request.getSource().name(),
            "accountId", request.getAccountId(),
            "bookId", request.getBookId(),
            "securityId", request.getSecurityId()
        );
    }
}

