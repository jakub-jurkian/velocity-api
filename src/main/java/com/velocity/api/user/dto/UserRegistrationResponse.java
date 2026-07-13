package com.velocity.api.user.dto;

import com.velocity.api.common.City;
import com.velocity.api.user.UserRole;

import java.util.UUID;

// This record explicitly excludes the password hash to prevent data leaks
public record UserRegistrationResponse(
        UUID id,
        String email,
        String fullName,
        String phone,
        City city,
        UserRole role
) {}
