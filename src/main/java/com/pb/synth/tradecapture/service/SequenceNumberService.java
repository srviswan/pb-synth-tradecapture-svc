package com.pb.synth.tradecapture.service;

import com.pb.synth.tradecapture.repository.PartitionStateRepository;
import com.pb.synth.tradecapture.repository.entity.PartitionStateEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Service for managing sequence numbers per partition.
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
     * 
     * @param partitionKey The partition key
     * @param sequenceNumber The incoming sequence number
     * @return Validation result
     */
    @Transactional
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
     * Get the expected next sequence number for a partition.
     */
    public long getExpectedSequenceNumber(String partitionKey) {
        // Try Redis cache first
        String cacheKey = SEQUENCE_PREFIX + partitionKey;
        String cached = redisTemplate.opsForValue().get(cacheKey);
        
        if (cached != null) {
            try {
                return Long.parseLong(cached) + 1;
            } catch (NumberFormatException e) {
                log.warn("Invalid sequence number in cache for partition: {}", partitionKey);
            }
        }
        
        // Fallback to database
        Optional<PartitionStateEntity> stateOpt = partitionStateRepository.findByPartitionKey(partitionKey);
        if (stateOpt.isPresent()) {
            long lastSequence = stateOpt.get().getLastSequenceNumber();
            // Cache it
            redisTemplate.opsForValue().set(cacheKey, String.valueOf(lastSequence), 
                SEQUENCE_CACHE_TTL.toSeconds(), TimeUnit.SECONDS);
            return lastSequence + 1;
        }
        
        // First message for this partition
        return 1L;
    }
    
    /**
     * Update sequence number for a partition.
     */
    private void updateSequenceNumber(String partitionKey, long sequenceNumber) {
        // Update database
        Optional<PartitionStateEntity> stateOpt = partitionStateRepository.findByPartitionKey(partitionKey);
        if (stateOpt.isPresent()) {
            PartitionStateEntity entity = stateOpt.get();
            entity.setLastSequenceNumber(sequenceNumber);
            partitionStateRepository.save(entity);
        } else {
            // Create new state with sequence number
            PartitionStateEntity entity = PartitionStateEntity.builder()
                .partitionKey(partitionKey)
                .lastSequenceNumber(sequenceNumber)
                .build();
            partitionStateRepository.save(entity);
        }
        
        // Update cache
        String cacheKey = SEQUENCE_PREFIX + partitionKey;
        redisTemplate.opsForValue().set(cacheKey, String.valueOf(sequenceNumber), 
            SEQUENCE_CACHE_TTL.toSeconds(), TimeUnit.SECONDS);
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

