package com.pcrm.backend.exception;

public class InsufficientQuotaException extends RuntimeException {

    public InsufficientQuotaException(long currentRemainingMinutes, long requiredMinutes) {
        super("Insufficient quota. Current remaining minutes: %d, required: %d"
                .formatted(currentRemainingMinutes, requiredMinutes));
    }
}
