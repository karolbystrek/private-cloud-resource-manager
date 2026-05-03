package com.pcrm.backend.idempotency.exception;

public class IdempotencyInProgressException extends RuntimeException {

    public IdempotencyInProgressException() {
        super("Request for this idempotency key is still being processed. Please retry shortly.");
    }
}
