package com.velocity.api.user.dto;

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
