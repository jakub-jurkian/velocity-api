package com.velocity.api.user.dto;

import com.velocity.api.common.City;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

@Valid
public record UserRegistrationDto(
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be properly formatted")
        String email,
        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 50, message = "Password must be between 8 and 50 characters")
        String password,
        @NotBlank(message = "Full name is required")
        @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
        String fullName,
        @NotNull(message = "City is required")
        City city,
        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Phone must be a valid international format (e.g., +48123456789)")
        String phone
) {
    // compact constructor
    public UserRegistrationDto {
        if (email != null) {
            email = email.toLowerCase().trim();
        }
        if (fullName != null) {
            fullName = fullName.trim();
        }
    }
}
