package com.velocity.api.reservation.dto;

import com.velocity.api.bike.BikeCategory;
import com.velocity.api.bike.repository.projection.AvailableModelProjection;

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
    public static AvailableModelResponse from(AvailableModelProjection projection, BigDecimal totalCost) {
        return new AvailableModelResponse(
                projection.getBookableInstanceId(),
                projection.getModelName(),
                projection.getModelDescription(),
                projection.getModelSpeed(),
                projection.getModelRange(),
                projection.getModelCapacity(),
                projection.getModelCategory(),
                totalCost
        );
    }
}
