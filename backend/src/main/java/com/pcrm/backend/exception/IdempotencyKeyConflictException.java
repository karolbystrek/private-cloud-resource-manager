package com.pcrm.backend.exception;

public class IdempotencyKeyConflictException extends RuntimeException {

    public IdempotencyKeyConflictException() {
        super("Idempotency key is already used for a different job submission payload");
    }
}
