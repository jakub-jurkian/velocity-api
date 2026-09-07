package com.velocity.api.user.dto;

import com.velocity.api.user.User;
import com.velocity.api.user.UserRole;
import com.velocity.api.user.UserStatus;

import java.time.LocalDate;
import java.util.UUID;

public record AdminUserResponse(
        UUID id,
        String email,
        String fullName,
        String phone,
        UserStatus status,
        UserRole role,
        LocalDate joinedDate
) {
    public static AdminUserResponse from(User user) {
        return new AdminUserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.getStatus(),
                user.getRole(),
                user.getJoinedDate());
    }
}
