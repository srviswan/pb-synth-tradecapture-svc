package com.pb.synth.tradecapture.repository;

import com.pb.synth.tradecapture.repository.entity.SwapBlotterEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for SwapBlotter persistence.
 */
@Repository
public interface SwapBlotterRepository extends JpaRepository<SwapBlotterEntity, Long> {

    /**
     * Find SwapBlotter by trade ID (non-archived only).
     */
    @Query("SELECT s FROM SwapBlotterEntity s WHERE s.tradeId = :tradeId AND s.archiveFlag = false")
    Optional<SwapBlotterEntity> findByTradeId(@Param("tradeId") String tradeId);

    /**
     * Find SwapBlotter by partition key (non-archived only).
     */
    @Query("SELECT s FROM SwapBlotterEntity s WHERE s.partitionKey = :partitionKey AND s.archiveFlag = false ORDER BY s.createdAt DESC")
    Optional<SwapBlotterEntity> findLatestByPartitionKey(@Param("partitionKey") String partitionKey);

    /**
     * Check if trade ID exists (non-archived only).
     */
    @Query("SELECT COUNT(s) > 0 FROM SwapBlotterEntity s WHERE s.tradeId = :tradeId AND s.archiveFlag = false")
    boolean existsByTradeId(@Param("tradeId") String tradeId);

    /**
     * Find archived SwapBlotter by trade ID.
     */
    @Query("SELECT s FROM SwapBlotterEntity s WHERE s.tradeId = :tradeId AND s.archiveFlag = true")
    Optional<SwapBlotterEntity> findArchivedByTradeId(@Param("tradeId") String tradeId);

    /**
     * Find all non-archived SwapBlotters by partition key.
     */
    @Query("SELECT s FROM SwapBlotterEntity s WHERE s.partitionKey = :partitionKey AND s.archiveFlag = false ORDER BY s.createdAt DESC")
    List<SwapBlotterEntity> findAllByPartitionKey(@Param("partitionKey") String partitionKey);
}
