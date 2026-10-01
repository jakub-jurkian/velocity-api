package com.velocity.api.security.exception;

 // A logout that could not be recorded because the token blacklist (Redis) is unreachable.
 // Reported as 503 rather than a success: the token would otherwise keep working.
public class TokenRevocationUnavailableException extends RuntimeException {
    public TokenRevocationUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
