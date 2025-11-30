package com.pb.synth.tradecapture.service;

import com.pb.synth.tradecapture.model.TradeCaptureRequest;
import com.pb.synth.tradecapture.repository.PartitionStateRepository;
import com.pb.synth.tradecapture.repository.entity.PartitionStateEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Service for managing sequence numbers per partition.
 * Sequence numbers are derived from tradeDate + tradeTimestamp to ensure in-order processing.
 * Detects out-of-order messages and gaps in sequence.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SequenceNumberService {
    
    private final PartitionStateRepository partitionStateRepository;
    private final StringRedisTemplate redisTemplate;
    
    private static final String SEQUENCE_PREFIX = "seq:partition:";
    private static final Duration SEQUENCE_CACHE_TTL = Duration.ofHours(24);
    
    /**
     * Validate and update sequence number for a partition.
     * Sequence number is derived from tradeDate + tradeTimestamp.
     * 
     * @param request The trade capture request
     * @return Validation result
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SequenceValidationResult validateAndUpdateSequence(TradeCaptureRequest request) {
        String partitionKey = request.getPartitionKey();
        Long sequenceNumber = request.getSequenceNumber();
        
        if (sequenceNumber == null) {
            // No sequence number - skip validation
            return SequenceValidationResult.builder()
                .valid(true)
                .build();
        }
        
        // Get expected sequence number
        long expectedSequence = getExpectedSequenceNumber(partitionKey);
        
        if (sequenceNumber < expectedSequence) {
            log.warn("Out-of-order message detected for partition: {}, expected: {}, received: {}", 
                partitionKey, expectedSequence, sequenceNumber);
            return SequenceValidationResult.builder()
                .valid(false)
                .reason("OUT_OF_ORDER")
                .expectedSequence(expectedSequence)
                .receivedSequence(sequenceNumber)
                .build();
        }
        
        if (sequenceNumber > expectedSequence) {
            long gap = sequenceNumber - expectedSequence;
            log.warn("Sequence gap detected for partition: {}, expected: {}, received: {}, gap: {}", 
                partitionKey, expectedSequence, sequenceNumber, gap);
            return SequenceValidationResult.builder()
                .valid(false)
                .reason("GAP_DETECTED")
                .expectedSequence(expectedSequence)
                .receivedSequence(sequenceNumber)
                .gap(gap)
                .build();
        }
        
        // Sequence is valid, update it
        updateSequenceNumber(partitionKey, sequenceNumber);
        
        return SequenceValidationResult.builder()
            .valid(true)
            .expectedSequence(expectedSequence)
            .receivedSequence(sequenceNumber)
            .build();
    }
    
    /**
     * Validate sequence number for a partition (overload for direct sequence number).
     * 
     * @param partitionKey The partition key
     * @param sequenceNumber The incoming sequence number
     * @return Validation result
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SequenceValidationResult validateAndUpdateSequence(String partitionKey, long sequenceNumber) {
        // Get expected sequence number
        long expectedSequence = getExpectedSequenceNumber(partitionKey);
        
        if (sequenceNumber < expectedSequence) {
            log.warn("Out-of-order message detected for partition: {}, expected: {}, received: {}", 
                partitionKey, expectedSequence, sequenceNumber);
            return SequenceValidationResult.builder()
                .valid(false)
                .reason("OUT_OF_ORDER")
                .expectedSequence(expectedSequence)
                .receivedSequence(sequenceNumber)
                .build();
        }
        
        if (sequenceNumber > expectedSequence) {
            long gap = sequenceNumber - expectedSequence;
            log.warn("Sequence gap detected for partition: {}, expected: {}, received: {}, gap: {}", 
                partitionKey, expectedSequence, sequenceNumber, gap);
            return SequenceValidationResult.builder()
                .valid(false)
                .reason("GAP_DETECTED")
                .expectedSequence(expectedSequence)
                .receivedSequence(sequenceNumber)
                .gap(gap)
                .build();
        }
        
        // Sequence is valid, update it
        updateSequenceNumber(partitionKey, sequenceNumber);
        
        return SequenceValidationResult.builder()
            .valid(true)
            .expectedSequence(expectedSequence)
            .receivedSequence(sequenceNumber)
            .build();
    }
    
    /**
     * Get the highest processed sequence number for a partition.
     * This is the highest sequence number we've successfully processed, not last + 1.
     * The expected next sequence would be highestProcessed + 1.
     */
    public long getExpectedSequenceNumber(String partitionKey) {
        // Try Redis cache first
        String cacheKey = SEQUENCE_PREFIX + partitionKey;
        String cached = redisTemplate.opsForValue().get(cacheKey);
        
        if (cached != null) {
            try {
                long highestSequence = Long.parseLong(cached);
                // Return highest processed (expected next would be highest + 1)
                return highestSequence;
            } catch (NumberFormatException e) {
                log.warn("Invalid sequence number in cache for partition: {}", partitionKey);
            }
        }
        
        // Fallback to database
        Optional<PartitionStateEntity> stateOpt = partitionStateRepository.findByPartitionKey(partitionKey);
        if (stateOpt.isPresent()) {
            long highestSequence = stateOpt.get().getLastSequenceNumber();
            if (highestSequence > 0) {
                // Cache it
                redisTemplate.opsForValue().set(cacheKey, String.valueOf(highestSequence), 
                    SEQUENCE_CACHE_TTL.toSeconds(), TimeUnit.SECONDS);
                return highestSequence;
            }
        }
        
        // First message for this partition - no sequence processed yet
        return 0L;
    }
    
    /**
     * Update highest processed sequence number for a partition.
     * We track the highest sequence processed, not just the last one.
     */
    private void updateSequenceNumber(String partitionKey, long sequenceNumber) {
        // Update database - use max to ensure we track highest
        Optional<PartitionStateEntity> stateOpt = partitionStateRepository.findByPartitionKey(partitionKey);
        if (stateOpt.isPresent()) {
            PartitionStateEntity entity = stateOpt.get();
            // Only update if this sequence is higher than what we have
            long currentHighest = entity.getLastSequenceNumber();
            if (sequenceNumber > currentHighest) {
                entity.setLastSequenceNumber(sequenceNumber);
                partitionStateRepository.save(entity);
            }
        } else {
            // Create new state with sequence number
            PartitionStateEntity entity = PartitionStateEntity.builder()
                .partitionKey(partitionKey)
                .lastSequenceNumber(sequenceNumber)
                .build();
            partitionStateRepository.save(entity);
        }

        // Update cache - use max to ensure we track highest
        String cacheKey = SEQUENCE_PREFIX + partitionKey;
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                long currentHighest = Long.parseLong(cached);
                if (sequenceNumber > currentHighest) {
                    redisTemplate.opsForValue().set(cacheKey, String.valueOf(sequenceNumber), 
                        SEQUENCE_CACHE_TTL.toSeconds(), TimeUnit.SECONDS);
                }
            } catch (NumberFormatException e) {
                // If cache is invalid, update it
                redisTemplate.opsForValue().set(cacheKey, String.valueOf(sequenceNumber), 
                    SEQUENCE_CACHE_TTL.toSeconds(), TimeUnit.SECONDS);
            }
        } else {
            redisTemplate.opsForValue().set(cacheKey, String.valueOf(sequenceNumber), 
                SEQUENCE_CACHE_TTL.toSeconds(), TimeUnit.SECONDS);
        }
    }
    
    @lombok.Data
    @lombok.Builder
    public static class SequenceValidationResult {
        private boolean valid;
        private String reason; // OUT_OF_ORDER, GAP_DETECTED, etc.
        private long expectedSequence;
        private long receivedSequence;
        private Long gap; // Only set if gap detected
    }
}
