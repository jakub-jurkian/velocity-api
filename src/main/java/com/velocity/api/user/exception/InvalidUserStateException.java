package com.velocity.api.user.exception;

public class InvalidUserStateException extends RuntimeException {
    public InvalidUserStateException(String message) {
        super(message);
    }
}
