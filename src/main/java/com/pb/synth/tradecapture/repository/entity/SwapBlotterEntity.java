package com.pb.synth.tradecapture.repository.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * JPA entity for SwapBlotter persistence.
 */
@Entity
@Table(name = "swap_blotter", indexes = {
    @Index(name = "idx_trade_id", columnList = "trade_id"),
    @Index(name = "idx_partition_key", columnList = "partition_key")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SwapBlotterEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trade_id", unique = true, nullable = false)
    private String tradeId;

    @Column(name = "partition_key", nullable = false)
    private String partitionKey;

    @Column(name = "swap_blotter_json", columnDefinition = "TEXT")
    private String swapBlotterJson; // JSON representation of SwapBlotter

    @Column(name = "version")
    @Version
    private Long version; // For optimistic locking

    @Column(name = "archive_flag", nullable = false)
    @Builder.Default
    private Boolean archiveFlag = false;

    @Column(name = "created_at", columnDefinition = "DATETIME2")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", columnDefinition = "DATETIME2")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

