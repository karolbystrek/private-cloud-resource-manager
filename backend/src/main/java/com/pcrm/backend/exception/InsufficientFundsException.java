package com.pcrm.backend.exception;

public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(long currentBalance, long requiredAmount) {
        super("Insufficient funds. Current balance: %d, required: %d".formatted(currentBalance, requiredAmount));
    }
}
