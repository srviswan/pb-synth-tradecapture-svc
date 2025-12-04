package com.pb.synth.tradecapture.service;

import com.pb.synth.tradecapture.cache.DistributedCacheService;
import com.pb.synth.tradecapture.model.SwapBlotter;
import com.pb.synth.tradecapture.model.TradeCaptureRequest;
import com.pb.synth.tradecapture.repository.IdempotencyRepository;
import com.pb.synth.tradecapture.repository.entity.IdempotencyRecordEntity;
import com.pb.synth.tradecapture.repository.entity.IdempotencyStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Service for handling idempotency and duplicate detection.
 * Uses distributed cache (L1) + Database (L2) for optimal performance.
 * Supports both Redis and Hazelcast via abstraction layer.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    private final IdempotencyRepository idempotencyRepository;
    private final SwapBlotterService swapBlotterService;
    private final DistributedCacheService distributedCacheService;

    @Value("${idempotency.window-hours:24}")
    private int idempotencyWindowHours;

    @Value("${idempotency.cache.enabled:true}")
    private boolean cacheEnabled;

    @Value("${idempotency.cache.key-prefix:idempotency:}")
    private String cacheKeyPrefix;

    @Value("${idempotency.cache.ttl-seconds:43200}")
    private long cacheTtlSeconds;

    private static final String CACHE_KEY_FORMAT = "%s%s"; // prefix + idempotencyKey
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_FAILED = "FAILED";

    /**
     * Check if trade is duplicate and return cached result if exists.
     * Uses Redis cache (L1) first, then database (L2) as fallback.
     */
    @Transactional(readOnly = true)
    public Optional<SwapBlotter> checkDuplicate(TradeCaptureRequest request) {
        String idempotencyKey = request.getIdempotencyKey();
        
        // Try distributed cache first (L1)
        if (cacheEnabled) {
            Optional<SwapBlotter> cached = checkCache(idempotencyKey);
            if (cached.isPresent()) {
                log.debug("Found idempotency record in cache: {}", idempotencyKey);
                return cached;
            }
        }
        
        // Fallback to database (L2)
        Optional<IdempotencyRecordEntity> existing = idempotencyRepository.findByIdempotencyKey(idempotencyKey);
        
        if (existing.isPresent()) {
            IdempotencyRecordEntity record = existing.get();
            
            // Check if expired
            if (record.getExpiresAt().isBefore(LocalDateTime.now())) {
                log.debug("Idempotency record expired for key: {}", idempotencyKey);
                return Optional.empty();
            }
            
            // Check status
            if (record.getStatus() == IdempotencyStatus.COMPLETED) {
                log.info("Duplicate trade detected: {}", idempotencyKey);
                // Cache in distributed cache for future lookups
                if (cacheEnabled) {
                    cacheInDistributedCache(idempotencyKey, STATUS_COMPLETED, record.getSwapBlotterId());
                }
                // Return cached SwapBlotter
                if (record.getSwapBlotterId() != null) {
                    return swapBlotterService.getSwapBlotterByTradeId(record.getTradeId());
                }
            } else if (record.getStatus() == IdempotencyStatus.PROCESSING) {
                log.warn("Trade is still being processed: {}", idempotencyKey);
                // Cache processing status to avoid repeated DB lookups
                if (cacheEnabled) {
                    cacheInDistributedCache(idempotencyKey, STATUS_PROCESSING, null);
                }
            }
        }
        
        return Optional.empty();
    }

    /**
     * Check distributed cache for idempotency record.
     */
    private Optional<SwapBlotter> checkCache(String idempotencyKey) {
        try {
            String cacheKey = String.format(CACHE_KEY_FORMAT, cacheKeyPrefix, idempotencyKey);
            Optional<String> statusOpt = distributedCacheService.get(cacheKey);
            
            if (statusOpt.isPresent()) {
                String status = statusOpt.get();
                if (STATUS_COMPLETED.equals(status)) {
                    // Get swapBlotterId from a separate cache key
                    String swapBlotterIdKey = cacheKey + ":swapBlotterId";
                    Optional<String> swapBlotterIdOpt = distributedCacheService.get(swapBlotterIdKey);
                    if (swapBlotterIdOpt.isPresent()) {
                        // Get tradeId from another cache key
                        String tradeIdKey = cacheKey + ":tradeId";
                        Optional<String> tradeIdOpt = distributedCacheService.get(tradeIdKey);
                        if (tradeIdOpt.isPresent()) {
                            return swapBlotterService.getSwapBlotterByTradeId(tradeIdOpt.get());
                        }
                    }
                } else if (STATUS_PROCESSING.equals(status)) {
                    // Trade is being processed, return empty to indicate duplicate
                    log.debug("Trade is processing (from cache): {}", idempotencyKey);
                    return Optional.empty();
                }
            }
        } catch (Exception e) {
            log.warn("Error checking cache for idempotency key: {}", idempotencyKey, e);
            // Fall through to database lookup
        }
        
        return Optional.empty();
    }

    /**
     * Cache idempotency status in distributed cache.
     */
    private void cacheInDistributedCache(String idempotencyKey, String status, String swapBlotterId) {
        try {
            String cacheKey = String.format(CACHE_KEY_FORMAT, cacheKeyPrefix, idempotencyKey);
            distributedCacheService.set(cacheKey, status, Duration.ofSeconds(cacheTtlSeconds));
            
            if (swapBlotterId != null) {
                String swapBlotterIdKey = cacheKey + ":swapBlotterId";
                distributedCacheService.set(swapBlotterIdKey, swapBlotterId, Duration.ofSeconds(cacheTtlSeconds));
            }
        } catch (Exception e) {
            log.warn("Error caching idempotency record: {}", idempotencyKey, e);
            // Non-critical, continue without caching
        }
    }

    /**
     * Create idempotency record for new trade.
     * Uses REQUIRES_NEW to isolate deadlocks from outer transaction.
     * 
     * If a duplicate key violation occurs (race condition), returns the existing record.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public IdempotencyRecordEntity createIdempotencyRecord(TradeCaptureRequest request) {
        String idempotencyKey = request.getIdempotencyKey();
        String partitionKey = request.getPartitionKey();
        
        // Double-check for existing record (defensive check before insert)
        Optional<IdempotencyRecordEntity> existing = idempotencyRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.debug("Idempotency record already exists (race condition detected): {}", idempotencyKey);
            return existing.get();
        }
        
        IdempotencyRecordEntity record = IdempotencyRecordEntity.builder()
            .idempotencyKey(idempotencyKey)
            .tradeId(request.getTradeId())
            .partitionKey(partitionKey)
            .status(IdempotencyStatus.PROCESSING)
            .createdAt(LocalDateTime.now())
            .expiresAt(LocalDateTime.now().plusHours(idempotencyWindowHours))
            .build();
        
        try {
            IdempotencyRecordEntity saved = idempotencyRepository.save(record);
            
            // Cache in distributed cache immediately
            if (cacheEnabled) {
                cacheInDistributedCache(idempotencyKey, STATUS_PROCESSING, null);
                // Cache tradeId for later lookup
                try {
                    String cacheKey = String.format(CACHE_KEY_FORMAT, cacheKeyPrefix, idempotencyKey);
                    String tradeIdKey = cacheKey + ":tradeId";
                    distributedCacheService.set(tradeIdKey, request.getTradeId(), Duration.ofSeconds(cacheTtlSeconds));
                } catch (Exception e) {
                    log.warn("Error caching tradeId", e);
                }
            }
            
            return saved;
        } catch (DataIntegrityViolationException e) {
            // Handle race condition: another thread inserted the record between our check and insert
            log.debug("Duplicate idempotency key detected (race condition): {}, returning existing record", idempotencyKey);
            
            // Retrieve the existing record that was just created by another thread
            Optional<IdempotencyRecordEntity> existingRecord = idempotencyRepository.findByIdempotencyKey(idempotencyKey);
            if (existingRecord.isPresent()) {
                return existingRecord.get();
            }
            
            // This shouldn't happen, but handle gracefully
            log.warn("Idempotency record not found after duplicate key violation: {}", idempotencyKey);
            throw new RuntimeException("Failed to create idempotency record due to duplicate key violation", e);
        }
    }

    /**
     * Update idempotency record on successful processing.
     * Uses REQUIRES_NEW to isolate deadlocks from outer transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCompleted(String idempotencyKey, String swapBlotterId) {
        Optional<IdempotencyRecordEntity> recordOpt = idempotencyRepository.findByIdempotencyKey(idempotencyKey);
        if (recordOpt.isPresent()) {
            IdempotencyRecordEntity record = recordOpt.get();
            record.setStatus(IdempotencyStatus.COMPLETED);
            record.setSwapBlotterId(swapBlotterId);
            record.setCompletedAt(LocalDateTime.now());
            idempotencyRepository.save(record);
            
            // Update distributed cache
            if (cacheEnabled) {
                cacheInDistributedCache(idempotencyKey, STATUS_COMPLETED, swapBlotterId);
            }
        }
    }

    /**
     * Update idempotency record on failure.
     * Uses REQUIRES_NEW to isolate deadlocks from outer transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(String idempotencyKey) {
        Optional<IdempotencyRecordEntity> recordOpt = idempotencyRepository.findByIdempotencyKey(idempotencyKey);
        if (recordOpt.isPresent()) {
            IdempotencyRecordEntity record = recordOpt.get();
            record.setStatus(IdempotencyStatus.FAILED);
            record.setCompletedAt(LocalDateTime.now());
            idempotencyRepository.save(record);
            
            // Update distributed cache
            if (cacheEnabled) {
                try {
                    String cacheKey = String.format(CACHE_KEY_FORMAT, cacheKeyPrefix, idempotencyKey);
                    distributedCacheService.set(cacheKey, STATUS_FAILED, Duration.ofSeconds(cacheTtlSeconds));
                } catch (Exception e) {
                    log.warn("Error updating cache for failed idempotency", e);
                }
            }
        }
    }

    /**
     * Archive expired idempotency records (instead of deleting).
     */
    @Transactional
    public void archiveExpiredRecords() {
        idempotencyRepository.archiveExpiredRecords(LocalDateTime.now());
        log.info("Archived expired idempotency records");
    }
}
