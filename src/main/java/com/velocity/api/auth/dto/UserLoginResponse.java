package com.velocity.api.auth.dto;

public record UserLoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn) {
}
