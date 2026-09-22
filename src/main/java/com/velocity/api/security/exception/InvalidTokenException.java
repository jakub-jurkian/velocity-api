package com.velocity.api.security.exception;

import org.springframework.security.core.AuthenticationException;

/**
 * A bearer token that cannot be trusted: malformed, expired, revoked, or
 * missing the subject it needs to identify anyone.
 *
 * <p>Distinct from {@link org.springframework.security.authentication.BadCredentialsException}
 * on purpose. That one is thrown by the {@code AuthenticationManager} on the login
 * path and means precisely one thing — the submitted email and password do not
 * match — which is what lets the handler answer it with a password-specific
 * message. The filter used to reuse it for token failures, so an ordinary
 * expired session told the user their password was wrong and sent them off to
 * reset a password that worked fine.
 */
public class InvalidTokenException extends AuthenticationException {

    public InvalidTokenException(String message) {
        super(message);
    }

    public InvalidTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
