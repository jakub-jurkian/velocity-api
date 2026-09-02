package com.velocity.api.user.dto;

import com.velocity.api.user.UserRole;
import jakarta.validation.constraints.NotNull;

public record AdminUserUpdateRoleRequest(
        @NotNull UserRole role
) {
}
