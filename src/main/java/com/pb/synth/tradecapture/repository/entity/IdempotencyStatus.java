package com.pb.synth.tradecapture.repository.entity;

/**
 * Idempotency status enumeration.
 */
public enum IdempotencyStatus {
    PROCESSING,
    COMPLETED,
    FAILED
}

