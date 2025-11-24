package com.pb.synth.tradecapture.service;

import com.pb.synth.tradecapture.repository.IdempotencyRepository;
import com.pb.synth.tradecapture.repository.PartitionStateRepository;
import com.pb.synth.tradecapture.repository.SwapBlotterRepository;
import com.pb.synth.tradecapture.repository.entity.IdempotencyRecordEntity;
import com.pb.synth.tradecapture.repository.entity.PartitionStateEntity;
import com.pb.synth.tradecapture.repository.entity.SwapBlotterEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * Service for archiving records.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ArchiveService {

    private final SwapBlotterRepository swapBlotterRepository;
    private final PartitionStateRepository partitionStateRepository;
    private final IdempotencyRepository idempotencyRepository;

    /**
     * Archive a specific trade by trade ID.
     */
    @Transactional
    public void archiveTrade(String tradeId) {
        log.info("Archiving trade: {}", tradeId);
        
        // Archive SwapBlotter
        swapBlotterRepository.findByTradeId(tradeId).ifPresent(entity -> {
            entity.setArchiveFlag(true);
            entity.setUpdatedAt(ZonedDateTime.now());
            swapBlotterRepository.save(entity);
        });
        
        // Archive related partition state
        swapBlotterRepository.findByTradeId(tradeId).ifPresent(blotter -> {
            partitionStateRepository.findByPartitionKey(blotter.getPartitionKey())
                .ifPresent(state -> {
                    state.setArchiveFlag(true);
                    state.setUpdatedAt(ZonedDateTime.now());
                    partitionStateRepository.save(state);
                });
        });
        
        // Archive idempotency record
        idempotencyRepository.findByTradeId(tradeId).ifPresent(record -> {
            record.setArchiveFlag(true);
            record.setCompletedAt(ZonedDateTime.now());
            idempotencyRepository.save(record);
        });
        
        log.info("Successfully archived trade: {}", tradeId);
    }

    /**
     * Archive records by date range.
     */
    @Transactional
    public ArchiveResult archiveByDateRange(ZonedDateTime startDate, ZonedDateTime endDate) {
        log.info("Archiving records from {} to {}", startDate, endDate);
        
        int swapBlotterCount = 0;
        int partitionStateCount = 0;
        int idempotencyCount = 0;
        
        // Archive SwapBlotter records
        List<SwapBlotterEntity> swapBlotters = swapBlotterRepository.findAll().stream()
            .filter(e -> Boolean.FALSE.equals(e.getArchiveFlag()))
            .filter(e -> e.getCreatedAt().isAfter(startDate) && e.getCreatedAt().isBefore(endDate))
            .toList();
        
        for (SwapBlotterEntity entity : swapBlotters) {
            entity.setArchiveFlag(true);
            entity.setUpdatedAt(ZonedDateTime.now());
            swapBlotterRepository.save(entity);
            swapBlotterCount++;
        }
        
        // Archive PartitionState records
        List<PartitionStateEntity> partitionStates = partitionStateRepository.findAll().stream()
            .filter(e -> Boolean.FALSE.equals(e.getArchiveFlag()))
            .filter(e -> e.getCreatedAt().isAfter(startDate) && e.getCreatedAt().isBefore(endDate))
            .toList();
        
        for (PartitionStateEntity entity : partitionStates) {
            entity.setArchiveFlag(true);
            entity.setUpdatedAt(ZonedDateTime.now());
            partitionStateRepository.save(entity);
            partitionStateCount++;
        }
        
        // Archive IdempotencyRecord records
        List<IdempotencyRecordEntity> idempotencyRecords = idempotencyRepository.findAll().stream()
            .filter(e -> Boolean.FALSE.equals(e.getArchiveFlag()))
            .filter(e -> e.getCreatedAt().isAfter(startDate) && e.getCreatedAt().isBefore(endDate))
            .toList();
        
        for (IdempotencyRecordEntity entity : idempotencyRecords) {
            entity.setArchiveFlag(true);
            entity.setCompletedAt(ZonedDateTime.now());
            idempotencyRepository.save(entity);
            idempotencyCount++;
        }
        
        log.info("Archived {} swap blotters, {} partition states, {} idempotency records", 
            swapBlotterCount, partitionStateCount, idempotencyCount);
        
        return ArchiveResult.builder()
            .swapBlotterCount(swapBlotterCount)
            .partitionStateCount(partitionStateCount)
            .idempotencyCount(idempotencyCount)
            .build();
    }

    /**
     * Archive expired idempotency records.
     */
    @Transactional
    public void archiveExpiredIdempotencyRecords() {
        log.info("Archiving expired idempotency records");
        idempotencyRepository.archiveExpiredRecords(ZonedDateTime.now());
    }

    @lombok.Data
    @lombok.Builder
    public static class ArchiveResult {
        private int swapBlotterCount;
        private int partitionStateCount;
        private int idempotencyCount;
    }
}

