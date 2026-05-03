package com.pcrm.backend.idempotency.service;

public record IdempotencyResult<T>(
        T response,
        boolean replayed
) {
}
