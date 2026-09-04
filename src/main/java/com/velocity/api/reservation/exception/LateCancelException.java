package com.velocity.api.reservation.exception;

public class LateCancelException extends RuntimeException {
    public LateCancelException(String message) {
        super(message);
    }
}
