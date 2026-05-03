package com.pcrm.backend.idempotency.domain;

public enum IdempotencyStatus {
    IN_PROGRESS,
    COMPLETED,
    FAILED_RETRYABLE,
    FAILED_FINAL
}
