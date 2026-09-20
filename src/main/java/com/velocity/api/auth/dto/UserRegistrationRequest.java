package com.velocity.api.auth.dto;

import com.velocity.api.common.City;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

@Valid
public record UserRegistrationRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be properly formatted")
        String email,
        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 64, message = "Password must be between 8 and 64 characters")
        // Composition rule enforced server-side; validators.ts mirrors it for the client.
        // Length is left to @Size so the two messages stay independently readable.
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).*$",
                message = "Password must contain an uppercase letter, a lowercase letter, a digit and a special character"
        )
        String password,
        @NotBlank(message = "Full name is required")
        @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
        String fullName,
        @NotNull(message = "City is required")
        City city,
        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "Phone must be a valid international format (e.g., +48123456789)")
        String phone
) {
    // compact constructor - normalizes before bean validation inspects the values
    public UserRegistrationRequest {
        if (email != null) email = email.trim().toLowerCase();
        if (fullName != null) fullName = fullName.trim();
        if (phone != null) phone = phone.trim();
    }
}
