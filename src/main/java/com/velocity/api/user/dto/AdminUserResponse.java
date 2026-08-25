package com.velocity.api.user.dto;

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
}
