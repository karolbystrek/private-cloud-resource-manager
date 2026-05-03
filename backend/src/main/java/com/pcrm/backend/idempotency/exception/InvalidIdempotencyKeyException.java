package com.pcrm.backend.idempotency.exception;

public class InvalidIdempotencyKeyException extends RuntimeException {

    public InvalidIdempotencyKeyException() {
        super("Idempotency-Key header must be a valid UUID");
    }
}
