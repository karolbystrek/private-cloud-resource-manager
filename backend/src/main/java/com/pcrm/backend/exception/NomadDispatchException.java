package com.pcrm.backend.exception;

public class NomadDispatchException extends RuntimeException {

    public NomadDispatchException(String message) {
        super(message);
    }

    public NomadDispatchException(String message, Throwable cause) {
        super(message, cause);
    }
}
