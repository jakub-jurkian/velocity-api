package com.velocity.api.user.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

public record UserDto(
        UUID id,
        String email,
        String fullName,
        String phone,
        String role, // String for API flexibility
        String status,
        String city
) {}
