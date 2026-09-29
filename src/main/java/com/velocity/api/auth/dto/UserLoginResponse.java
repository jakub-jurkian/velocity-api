package com.velocity.api.auth.dto;

public record UserLoginResponse(
        String accessToken,
        String tokenType,
        // Seconds until the token expires, as in OAuth 2.0 token responses.
        long expiresIn) {
}
