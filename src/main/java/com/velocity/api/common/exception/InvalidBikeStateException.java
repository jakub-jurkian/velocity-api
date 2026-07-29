package com.velocity.api.common.exception;

public class InvalidBikeStateException extends RuntimeException {
    public InvalidBikeStateException(String message) {
        super(message);
    }
}
