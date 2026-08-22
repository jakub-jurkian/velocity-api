package com.velocity.api.user.dto;

public record UserLoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn) {
}
