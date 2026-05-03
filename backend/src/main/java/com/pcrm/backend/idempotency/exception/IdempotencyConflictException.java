package com.pcrm.backend.idempotency.exception;

public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException() {
        super("Idempotency key is already used for a different request payload");
    }
}
