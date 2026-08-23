package com.velocity.api.user.dto;

import com.velocity.api.common.City;
import com.velocity.api.user.UserRole;

import java.time.LocalDate;
import java.util.UUID;

public record UserProfileResponse(
        UUID id,
        String email,
        String fullName,
        String phone,
        UserRole role,
        City city,
        LocalDate joinedDate
) {
}
