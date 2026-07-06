package com.velocity.api.common.exception;

/**
 * A custom exception class.
 * It's thrown whenever a database query comes up empty.
 */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
