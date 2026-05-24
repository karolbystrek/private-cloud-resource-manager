package com.pcrm.backend.exception;

import java.util.List;

public class JobSubmissionValidationException extends RuntimeException {

    private final List<FieldError> errors;

    public JobSubmissionValidationException(String message, List<FieldError> errors) {
        super(message);
        this.errors = List.copyOf(errors);
    }

    public List<FieldError> getErrors() {
        return errors;
    }

    public record FieldError(String field, String message) {
    }
}
