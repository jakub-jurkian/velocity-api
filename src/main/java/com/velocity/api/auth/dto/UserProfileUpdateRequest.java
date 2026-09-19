package com.velocity.api.auth.dto;

import com.velocity.api.common.City;
import com.velocity.api.common.JsonNullables;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.openapitools.jackson.nullable.JsonNullable;

public record UserProfileUpdateRequest(
        JsonNullable<@NotBlank @Size(min = 2, max = 100) String> fullName,
        JsonNullable<@NotBlank @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "Phone must be a valid international format (e.g., +48123456789)") String> phone,
        JsonNullable<City> city
) {
    // compact constructor - normalizes before bean validation inspects the values
    public UserProfileUpdateRequest {
        fullName = JsonNullables.trimmed(fullName);
        phone = JsonNullables.trimmed(phone);
        city = city == null ? JsonNullable.undefined() : city;
    }
}
