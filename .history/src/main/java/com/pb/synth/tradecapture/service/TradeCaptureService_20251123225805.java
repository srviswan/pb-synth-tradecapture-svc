package com.pb.synth.tradecapture.service;

import com.pb.synth.tradecapture.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    @SuppressWarnings("unused")
    private final SequenceNumberService sequenceNumberService; // Will be used when sequence numbers are added to messages

    /**
     * Process trade capture request with partition locking and sequence validation.
     */
    @Transactional
    public TradeCaptureResponse processTrade(TradeCaptureRequest request) {
        long startTime = System.currentTimeMillis();
        String partitionKey = request.getPartitionKey();
        boolean lockAcquired = false;
        
        try {
            // 1. Acquire partition lock (distributed lock for cross-instance coordination)
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
            
            // 2. Validate sequence number (if provided in message)
            // Note: Sequence number would come from protobuf message
            // For now, we'll skip if not provided
            
            // 3. Check idempotency (double-check within lock)
            Optional<SwapBlotter> cached = idempotencyService.checkDuplicate(request);
            if (cached.isPresent()) {
                log.info("Returning cached SwapBlotter for tradeId: {}", request.getTradeId());
                return TradeCaptureResponse.builder()
                    .tradeId(request.getTradeId())
                    .status("DUPLICATE")
                    .swapBlotter(cached.get())
                    .build();
            }
            
            // 4. Create idempotency record
            idempotencyService.createIdempotencyRecord(request);
            
            // 3. Enrichment
            EnrichmentService.EnrichmentResult enrichmentResult = enrichmentService.enrich(request);
            
            // 4. Build initial SwapBlotter
            SwapBlotter swapBlotter = buildInitialSwapBlotter(request, enrichmentResult);
            
            // 5. Apply rules
            Map<String, Object> tradeData = buildTradeDataMap(request, enrichmentResult);
            swapBlotter = rulesEngine.applyRules(swapBlotter, tradeData);
            
            // 6. Validation
            validationService.validate(request);
            
            // 7. State management
            State newState = State.builder()
                .positionState(PositionStatusEnum.FORMED)
                .build();
            
            // Get current state for validation, then update
            stateManagementService.getState(request.getPartitionKey())
                .ifPresent(currentState -> {
                    stateManagementService.validateStateTransition(
                        currentState.getPositionState(), 
                        newState.getPositionState()
                    );
                });
            
            stateManagementService.updateState(request.getPartitionKey(), newState);
            swapBlotter.setState(newState);
            
            // 8. Save SwapBlotter
            swapBlotterService.saveSwapBlotter(swapBlotter);
            
            // 9. Update idempotency record
            idempotencyService.markCompleted(request.getIdempotencyKey(), swapBlotter.getTradeId());
            
            // 10. Publish to subscribers
            swapBlotterPublisherService.publish(swapBlotter);
            
            long processingTime = System.currentTimeMillis() - startTime;
            swapBlotter.getProcessingMetadata().setProcessingTimeMs(processingTime);
            
            return TradeCaptureResponse.builder()
                .tradeId(request.getTradeId())
                .status("SUCCESS")
                .swapBlotter(swapBlotter)
                .build();
                
        } catch (Exception e) {
            log.error("Error processing trade: {}", request.getTradeId(), e);
            idempotencyService.markFailed(request.getIdempotencyKey());
            
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

