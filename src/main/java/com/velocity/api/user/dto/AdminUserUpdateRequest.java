package com.velocity.api.user.dto;

import com.velocity.api.common.City;
import org.openapitools.jackson.nullable.JsonNullable;

public record AdminUserUpdateRequest(
        JsonNullable<String> fullName,
        JsonNullable<String> phone,
        JsonNullable<City> city,
        JsonNullable<String> email
) {
}
