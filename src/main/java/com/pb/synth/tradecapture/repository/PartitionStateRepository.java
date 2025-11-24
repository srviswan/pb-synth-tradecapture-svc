package com.pb.synth.tradecapture.repository;

import com.pb.synth.tradecapture.repository.entity.PartitionStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;

/**
 * Repository for partition state persistence.
 */
@Repository
public interface PartitionStateRepository extends JpaRepository<PartitionStateEntity, Long> {

    /**
     * Find partition state by partition key with pessimistic lock (non-archived only).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PartitionStateEntity p WHERE p.partitionKey = :partitionKey AND p.archiveFlag = false")
    Optional<PartitionStateEntity> findByPartitionKeyWithLock(@Param("partitionKey") String partitionKey);

    /**
     * Find partition state by partition key (non-archived only).
     */
    @Query("SELECT p FROM PartitionStateEntity p WHERE p.partitionKey = :partitionKey AND p.archiveFlag = false")
    Optional<PartitionStateEntity> findByPartitionKey(@Param("partitionKey") String partitionKey);

    /**
     * Check if partition key exists (non-archived only).
     */
    @Query("SELECT COUNT(p) > 0 FROM PartitionStateEntity p WHERE p.partitionKey = :partitionKey AND p.archiveFlag = false")
    boolean existsByPartitionKey(@Param("partitionKey") String partitionKey);
}

