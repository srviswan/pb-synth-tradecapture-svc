package com.pb.synth.tradecapture.service;

import com.pb.synth.tradecapture.model.SwapBlotter;
import com.pb.synth.tradecapture.model.TradeCaptureRequest;
import com.pb.synth.tradecapture.repository.IdempotencyRepository;
import com.pb.synth.tradecapture.repository.entity.IdempotencyRecordEntity;
import com.pb.synth.tradecapture.repository.entity.IdempotencyStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * Service for handling idempotency and duplicate detection.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    private final IdempotencyRepository idempotencyRepository;
    private final SwapBlotterService swapBlotterService;

    @Value("${idempotency.window-hours:24}")
    private int idempotencyWindowHours;

    /**
     * Check if trade is duplicate and return cached result if exists.
     */
    @Transactional(readOnly = true)
    public Optional<SwapBlotter> checkDuplicate(TradeCaptureRequest request) {
        String idempotencyKey = request.getIdempotencyKey();
        
        Optional<IdempotencyRecordEntity> existing = idempotencyRepository.findByIdempotencyKey(idempotencyKey);
        
        if (existing.isPresent()) {
            IdempotencyRecordEntity record = existing.get();
            
            // Check if expired
            if (record.getExpiresAt().isBefore(ZonedDateTime.now())) {
                log.debug("Idempotency record expired for key: {}", idempotencyKey);
                return Optional.empty();
            }
            
            // Check status
            if (record.getStatus() == IdempotencyStatus.COMPLETED) {
                log.info("Duplicate trade detected: {}", idempotencyKey);
                // Return cached SwapBlotter
                if (record.getSwapBlotterId() != null) {
                    return swapBlotterService.getSwapBlotterByTradeId(record.getTradeId());
                }
            } else if (record.getStatus() == IdempotencyStatus.PROCESSING) {
                log.warn("Trade is still being processed: {}", idempotencyKey);
                // Could return processing status or throw exception
            }
        }
        
        return Optional.empty();
    }

    /**
     * Create idempotency record for new trade.
     */
    @Transactional
    public IdempotencyRecordEntity createIdempotencyRecord(TradeCaptureRequest request) {
        String idempotencyKey = request.getIdempotencyKey();
        String partitionKey = request.getPartitionKey();
        
        IdempotencyRecordEntity record = IdempotencyRecordEntity.builder()
            .idempotencyKey(idempotencyKey)
            .tradeId(request.getTradeId())
            .partitionKey(partitionKey)
            .status(IdempotencyStatus.PROCESSING)
            .createdAt(ZonedDateTime.now())
            .expiresAt(ZonedDateTime.now().plusHours(idempotencyWindowHours))
            .build();
        
        return idempotencyRepository.save(record);
    }

    /**
     * Update idempotency record on successful processing.
     */
    @Transactional
    public void markCompleted(String idempotencyKey, String swapBlotterId) {
        Optional<IdempotencyRecordEntity> recordOpt = idempotencyRepository.findByIdempotencyKey(idempotencyKey);
        if (recordOpt.isPresent()) {
            IdempotencyRecordEntity record = recordOpt.get();
            record.setStatus(IdempotencyStatus.COMPLETED);
            record.setSwapBlotterId(swapBlotterId);
            record.setCompletedAt(ZonedDateTime.now());
            idempotencyRepository.save(record);
        }
    }

    /**
     * Update idempotency record on failure.
     */
    @Transactional
    public void markFailed(String idempotencyKey) {
        Optional<IdempotencyRecordEntity> recordOpt = idempotencyRepository.findByIdempotencyKey(idempotencyKey);
        if (recordOpt.isPresent()) {
            IdempotencyRecordEntity record = recordOpt.get();
            record.setStatus(IdempotencyStatus.FAILED);
            record.setCompletedAt(ZonedDateTime.now());
            idempotencyRepository.save(record);
        }
    }

    /**
     * Archive expired idempotency records (instead of deleting).
     */
    @Transactional
    public void archiveExpiredRecords() {
        idempotencyRepository.archiveExpiredRecords(ZonedDateTime.now());
        log.info("Archived expired idempotency records");
    }
}

