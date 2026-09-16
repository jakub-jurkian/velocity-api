package com.velocity.api.reservation.dto;

import com.velocity.api.bike.BikeCategory;
import com.velocity.api.bike.repository.projection.AvailableModelProjection;

import java.util.UUID;

public record AvailableBikeModel(
        UUID bookableInstanceId,
        String modelName,
        String modelDescription,
        int modelSpeed,
        int modelRange,
        int modelCapacity,
        BikeCategory modelCategory
) {
    public static AvailableBikeModel from(AvailableModelProjection projection) {
        return new AvailableBikeModel(
                projection.getBookableInstanceId(),
                projection.getModelName(),
                projection.getModelDescription(),
                projection.getModelSpeed(),
                projection.getModelRange(),
                projection.getModelCapacity(),
                projection.getModelCategory()
        );
    }
}
