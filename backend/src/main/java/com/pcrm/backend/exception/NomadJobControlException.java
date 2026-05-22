package com.pcrm.backend.exception;

public class NomadJobControlException extends RuntimeException {

    public NomadJobControlException(String message) {
        super(message);
    }

    public NomadJobControlException(String message, Throwable cause) {
        super(message, cause);
    }
}
