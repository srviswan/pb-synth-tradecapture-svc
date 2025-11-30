package com.pb.synth.tradecapture.service;

import com.pb.synth.tradecapture.model.SwapBlotter;
import com.pb.synth.tradecapture.model.TradeCaptureRequest;
import com.pb.synth.tradecapture.repository.IdempotencyRepository;
import com.pb.synth.tradecapture.repository.entity.IdempotencyRecordEntity;
import com.pb.synth.tradecapture.repository.entity.IdempotencyStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Service for handling idempotency and duplicate detection.
 * Uses Redis cache (L1) + Database (L2) for optimal performance.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    private final IdempotencyRepository idempotencyRepository;
    private final SwapBlotterService swapBlotterService;
    private final StringRedisTemplate redisTemplate;

    @Value("${idempotency.window-hours:24}")
    private int idempotencyWindowHours;

    @Value("${idempotency.redis.enabled:true}")
    private boolean redisCacheEnabled;

    @Value("${idempotency.redis.key-prefix:idempotency:}")
    private String redisKeyPrefix;

    @Value("${idempotency.redis.ttl-seconds:43200}")
    private long redisTtlSeconds;

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
        
        // Try Redis cache first (L1)
        if (redisCacheEnabled) {
            Optional<SwapBlotter> cached = checkRedisCache(idempotencyKey);
            if (cached.isPresent()) {
                log.debug("Found idempotency record in Redis cache: {}", idempotencyKey);
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
                // Cache in Redis for future lookups
                if (redisCacheEnabled) {
                    cacheInRedis(idempotencyKey, STATUS_COMPLETED, record.getSwapBlotterId());
                }
                // Return cached SwapBlotter
                if (record.getSwapBlotterId() != null) {
                    return swapBlotterService.getSwapBlotterByTradeId(record.getTradeId());
                }
            } else if (record.getStatus() == IdempotencyStatus.PROCESSING) {
                log.warn("Trade is still being processed: {}", idempotencyKey);
                // Cache processing status to avoid repeated DB lookups
                if (redisCacheEnabled) {
                    cacheInRedis(idempotencyKey, STATUS_PROCESSING, null);
                }
            }
        }
        
        return Optional.empty();
    }

    /**
     * Check Redis cache for idempotency record.
     */
    private Optional<SwapBlotter> checkRedisCache(String idempotencyKey) {
        try {
            String cacheKey = String.format(CACHE_KEY_FORMAT, redisKeyPrefix, idempotencyKey);
            String status = redisTemplate.opsForValue().get(cacheKey);
            
            if (status != null) {
                if (STATUS_COMPLETED.equals(status)) {
                    // Get swapBlotterId from a separate cache key
                    String swapBlotterIdKey = cacheKey + ":swapBlotterId";
                    String swapBlotterId = redisTemplate.opsForValue().get(swapBlotterIdKey);
                    if (swapBlotterId != null) {
                        // Get tradeId from another cache key
                        String tradeIdKey = cacheKey + ":tradeId";
                        String tradeId = redisTemplate.opsForValue().get(tradeIdKey);
                        if (tradeId != null) {
                            return swapBlotterService.getSwapBlotterByTradeId(tradeId);
                        }
                    }
                } else if (STATUS_PROCESSING.equals(status)) {
                    // Trade is being processed, return empty to indicate duplicate
                    log.debug("Trade is processing (from cache): {}", idempotencyKey);
                    return Optional.empty();
                }
            }
        } catch (Exception e) {
            log.warn("Error checking Redis cache for idempotency key: {}", idempotencyKey, e);
            // Fall through to database lookup
        }
        
        return Optional.empty();
    }

    /**
     * Cache idempotency status in Redis.
     */
    private void cacheInRedis(String idempotencyKey, String status, String swapBlotterId) {
        try {
            String cacheKey = String.format(CACHE_KEY_FORMAT, redisKeyPrefix, idempotencyKey);
            redisTemplate.opsForValue().set(cacheKey, status, Duration.ofSeconds(redisTtlSeconds));
            
            if (swapBlotterId != null) {
                String swapBlotterIdKey = cacheKey + ":swapBlotterId";
                redisTemplate.opsForValue().set(swapBlotterIdKey, swapBlotterId, Duration.ofSeconds(redisTtlSeconds));
            }
        } catch (Exception e) {
            log.warn("Error caching idempotency record in Redis: {}", idempotencyKey, e);
            // Non-critical, continue without caching
        }
    }

    /**
     * Create idempotency record for new trade.
     * Uses REQUIRES_NEW to isolate deadlocks from outer transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public IdempotencyRecordEntity createIdempotencyRecord(TradeCaptureRequest request) {
        String idempotencyKey = request.getIdempotencyKey();
        String partitionKey = request.getPartitionKey();
        
        IdempotencyRecordEntity record = IdempotencyRecordEntity.builder()
            .idempotencyKey(idempotencyKey)
            .tradeId(request.getTradeId())
            .partitionKey(partitionKey)
            .status(IdempotencyStatus.PROCESSING)
            .createdAt(LocalDateTime.now())
            .expiresAt(LocalDateTime.now().plusHours(idempotencyWindowHours))
            .build();
        
        IdempotencyRecordEntity saved = idempotencyRepository.save(record);
        
        // Cache in Redis immediately
        if (redisCacheEnabled) {
            cacheInRedis(idempotencyKey, STATUS_PROCESSING, null);
            // Cache tradeId for later lookup
            try {
                String cacheKey = String.format(CACHE_KEY_FORMAT, redisKeyPrefix, idempotencyKey);
                String tradeIdKey = cacheKey + ":tradeId";
                redisTemplate.opsForValue().set(tradeIdKey, request.getTradeId(), Duration.ofSeconds(redisTtlSeconds));
            } catch (Exception e) {
                log.warn("Error caching tradeId in Redis", e);
            }
        }
        
        return saved;
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
            
            // Update Redis cache
            if (redisCacheEnabled) {
                cacheInRedis(idempotencyKey, STATUS_COMPLETED, swapBlotterId);
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
            
            // Update Redis cache
            if (redisCacheEnabled) {
                try {
                    String cacheKey = String.format(CACHE_KEY_FORMAT, redisKeyPrefix, idempotencyKey);
                    redisTemplate.opsForValue().set(cacheKey, STATUS_FAILED, Duration.ofSeconds(redisTtlSeconds));
                } catch (Exception e) {
                    log.warn("Error updating Redis cache for failed idempotency", e);
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
