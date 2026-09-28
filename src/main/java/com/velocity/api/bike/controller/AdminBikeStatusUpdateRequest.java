package com.velocity.api.user.dto;

import com.velocity.api.bike.BikeStatus;
import jakarta.validation.constraints.NotNull;

public record AdminBikeStatusUpdateRequest(
        @NotNull BikeStatus status,
        @NotNull Long version
) {
}
