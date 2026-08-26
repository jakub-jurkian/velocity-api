package com.velocity.api.bike.exception;

public class InvalidBikeStateException extends RuntimeException {
    public InvalidBikeStateException(String message) {
        super(message);
    }
}
