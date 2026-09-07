package com.velocity.api.user.dto;

import com.velocity.api.common.City;
import org.openapitools.jackson.nullable.JsonNullable;

public record UserProfileUpdateRequest(
        JsonNullable<String> fullName,
        JsonNullable<String> phone,
        JsonNullable<City> city
) {
}
