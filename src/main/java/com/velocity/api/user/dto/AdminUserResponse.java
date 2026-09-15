package com.velocity.api.user.dto;

import com.velocity.api.user.User;
import com.velocity.api.user.UserRole;
import com.velocity.api.user.UserStatus;

import java.time.Instant;
import java.util.UUID;

public record AdminUserResponse(
        UUID id,
        String email,
        String fullName,
        String phone,
        UserStatus status,
        UserRole role,
        Instant createdAt
) {
    public static AdminUserResponse from(User user) {
        return new AdminUserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.getStatus(),
                user.getRole(),
                user.getCreatedAt());
    }
}
