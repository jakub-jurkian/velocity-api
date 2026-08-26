package com.velocity.api.bike.exception;

public class BikeNotAvailableException extends RuntimeException {
    public BikeNotAvailableException(String message) {
        super(message);
    }
}
