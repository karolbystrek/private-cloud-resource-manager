package com.pcrm.broker.exception;

import java.util.UUID;

/**
 * Thrown when a requested resource cannot be found.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resourceName, UUID id) {
        super("%s not found with id: %s".formatted(resourceName, id));
    }

    public ResourceNotFoundException(String resourceName, String field, String value) {
        super("%s not found with %s: %s".formatted(resourceName, field, value));
    }
}
