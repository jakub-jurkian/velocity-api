package com.velocity.api.bike.exception;

public class InvalidBikeStatusTransitionException extends RuntimeException {
    public InvalidBikeStatusTransitionException(String message) {
        super(message);
    }
}
