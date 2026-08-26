package com.velocity.api.auth.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Valid
public record UserLoginRequest(
        @NotBlank
        @Email
        String email,
        @NotBlank
        String password) {
}
