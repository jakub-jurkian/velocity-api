package com.velocity.api.user.exception;

public class CannotDemoteSelfException extends RuntimeException {
    public CannotDemoteSelfException(String message) {
        super(message);
    }
}
