package com.pb.synth.tradecapture.repository.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

/**
 * JPA entity for idempotency record persistence.
 */
@Entity
@Table(name = "idempotency_record", indexes = {
    @Index(name = "idx_idempotency_key", columnList = "idempotency_key", unique = true),
    @Index(name = "idx_trade_id", columnList = "trade_id"),
    @Index(name = "idx_expires_at", columnList = "expires_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdempotencyRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", unique = true, nullable = false)
    private String idempotencyKey;

    @Column(name = "trade_id", nullable = false)
    private String tradeId;

    @Column(name = "partition_key")
    private String partitionKey;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private IdempotencyStatus status;

    @Column(name = "swap_blotter_id")
    private String swapBlotterId; // Reference to processed SwapBlotter

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    @Column(name = "completed_at")
    private ZonedDateTime completedAt;

    @Column(name = "expires_at", nullable = false)
    private ZonedDateTime expiresAt;

    @PrePersist
    protected void onCreate() {
        createdAt = ZonedDateTime.now();
    }
}


