package com.velocity.api.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UserLoginRequest(
        @NotBlank
        @Email
        String email,
        @NotBlank
        String password) {
    public UserLoginRequest {
        if (email != null) email = email.trim().toLowerCase();
    }
}
