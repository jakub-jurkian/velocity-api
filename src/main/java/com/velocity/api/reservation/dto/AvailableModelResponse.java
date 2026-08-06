package com.velocity.api.reservation.dto;

import com.velocity.api.bike.BikeCategory;

import java.math.BigDecimal;
import java.util.UUID;

public record AvailableModelResponse(
        UUID bookableInstanceId,
        String modelName,
        String modelDescription,
        int modelSpeed,
        int modelRange,
        int modelCapacity,
        BikeCategory modelCategory,
        BigDecimal totalCost
) {
}
