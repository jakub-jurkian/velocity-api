package com.velocity.api.user.dto;

import com.velocity.api.common.City;
import com.velocity.api.common.JsonNullables;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.openapitools.jackson.nullable.JsonNullable;

public record AdminUserUpdateRequest(
        JsonNullable<@NotBlank @Size(min = 2, max = 100) String> fullName,
        JsonNullable<@NotBlank @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "Phone must be a valid international format (e.g., +48123456789)") String> phone,
        JsonNullable<City> city,
        JsonNullable<@NotBlank @Email String> email
) {
    // compact constructor - normalizes before bean validation inspects the values
    public AdminUserUpdateRequest {
        fullName = JsonNullables.trimmed(fullName);
        phone = JsonNullables.trimmed(phone);
        email = JsonNullables.map(email, s -> s.trim().toLowerCase());
        city = city == null ? JsonNullable.undefined() : city;
    }
}
