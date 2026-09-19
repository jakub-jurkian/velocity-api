package com.velocity.api.user.exception;

public class PhoneAlreadyRegisteredException extends RuntimeException {
    public PhoneAlreadyRegisteredException(String message) {
        super(message);
    }
}
