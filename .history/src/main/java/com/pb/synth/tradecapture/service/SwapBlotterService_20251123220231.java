package com.pb.synth.tradecapture.service;

import com.pb.synth.tradecapture.model.SwapBlotter;
import com.pb.synth.tradecapture.repository.SwapBlotterRepository;
import com.pb.synth.tradecapture.repository.entity.SwapBlotterEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * Service for SwapBlotter persistence and retrieval.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SwapBlotterService {

    private final SwapBlotterRepository swapBlotterRepository;
    private final ObjectMapper objectMapper;

    /**
     * Save SwapBlotter.
     */
    @Transactional
    public SwapBlotterEntity saveSwapBlotter(SwapBlotter swapBlotter) {
        try {
            String json = objectMapper.writeValueAsString(swapBlotter);
            
            SwapBlotterEntity entity = SwapBlotterEntity.builder()
                .tradeId(swapBlotter.getTradeId())
                .partitionKey(swapBlotter.getPartitionKey())
                .swapBlotterJson(json)
                .version(swapBlotter.getVersion() != null ? swapBlotter.getVersion() : 0L)
                .build();
            
            return swapBlotterRepository.save(entity);
        } catch (Exception e) {
            log.error("Error saving SwapBlotter", e);
            throw new RuntimeException("Failed to save SwapBlotter", e);
        }
    }

    /**
     * Get SwapBlotter by trade ID.
     */
    @Transactional(readOnly = true)
    public Optional<SwapBlotter> getSwapBlotterByTradeId(String tradeId) {
        return swapBlotterRepository.findByTradeId(tradeId)
            .map(this::convertToSwapBlotter);
    }

    /**
     * Get latest SwapBlotter by partition key.
     */
    @Transactional(readOnly = true)
    public Optional<SwapBlotter> getLatestSwapBlotterByPartitionKey(String partitionKey) {
        return swapBlotterRepository.findLatestByPartitionKey(partitionKey)
            .map(this::convertToSwapBlotter);
    }

    /**
     * Check if trade ID exists.
     */
    @Transactional(readOnly = true)
    public boolean existsByTradeId(String tradeId) {
        return swapBlotterRepository.existsByTradeId(tradeId);
    }

    private SwapBlotter convertToSwapBlotter(SwapBlotterEntity entity) {
        try {
            SwapBlotter swapBlotter = objectMapper.readValue(entity.getSwapBlotterJson(), SwapBlotter.class);
            swapBlotter.setVersion(entity.getVersion());
            return swapBlotter;
        } catch (Exception e) {
            log.error("Error converting SwapBlotterEntity to SwapBlotter", e);
            throw new RuntimeException("Failed to convert SwapBlotter", e);
        }
    }
}

