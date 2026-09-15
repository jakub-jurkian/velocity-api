package com.velocity.api.auth.dto;

import com.velocity.api.common.City;
import com.velocity.api.user.User;
import com.velocity.api.user.UserRole;

import java.time.Instant;
import java.util.UUID;

public record UserProfileResponse(
        UUID id,
        String email,
        String fullName,
        String phone,
        UserRole role,
        City city,
        Instant createdAt
) {
    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.getRole(),
                user.getCity(),
                user.getCreatedAt()
        );
    }
}
