package com.pb.synth.tradecapture.repository;

import com.pb.synth.tradecapture.repository.entity.IdempotencyRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * Repository for idempotency record persistence.
 */
@Repository
public interface IdempotencyRepository extends JpaRepository<IdempotencyRecordEntity, Long> {

    /**
     * Find idempotency record by idempotency key (non-archived only).
     */
    @Query("SELECT i FROM IdempotencyRecordEntity i WHERE i.idempotencyKey = :idempotencyKey AND i.archiveFlag = false")
    Optional<IdempotencyRecordEntity> findByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

    /**
     * Check if idempotency key exists (non-archived only).
     */
    @Query("SELECT COUNT(i) > 0 FROM IdempotencyRecordEntity i WHERE i.idempotencyKey = :idempotencyKey AND i.archiveFlag = false")
    boolean existsByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

    /**
     * Archive expired idempotency records (instead of deleting).
     */
    @Modifying
    @Query("UPDATE IdempotencyRecordEntity i SET i.archiveFlag = true, i.completedAt = :now WHERE i.expiresAt < :now AND i.archiveFlag = false")
    void archiveExpiredRecords(@Param("now") ZonedDateTime now);

    /**
     * Find idempotency record by trade ID (non-archived only).
     */
    @Query("SELECT i FROM IdempotencyRecordEntity i WHERE i.tradeId = :tradeId AND i.archiveFlag = false")
    Optional<IdempotencyRecordEntity> findByTradeId(@Param("tradeId") String tradeId);
}

