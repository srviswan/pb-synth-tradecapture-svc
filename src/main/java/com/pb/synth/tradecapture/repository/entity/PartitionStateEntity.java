package com.pb.synth.tradecapture.repository.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

/**
 * JPA entity for partition state persistence.
 */
@Entity
@Table(name = "partition_state", indexes = {
    @Index(name = "idx_partition_key_unique", columnList = "partition_key", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartitionStateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "partition_key", unique = true, nullable = false)
    private String partitionKey;

    @Column(name = "position_state")
    private String positionState; // PositionStatusEnum as string

    @Column(name = "last_sequence_number")
    private Long lastSequenceNumber;

    @Column(name = "state_json", columnDefinition = "TEXT")
    private String stateJson; // JSON representation of State

    @Column(name = "version")
    @Version
    private Long version; // For optimistic locking

    @Column(name = "archive_flag", nullable = false)
    @Builder.Default
    private Boolean archiveFlag = false;

    @Column(name = "created_at")
    private ZonedDateTime createdAt;

    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = ZonedDateTime.now();
        updatedAt = ZonedDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = ZonedDateTime.now();
    }
}

