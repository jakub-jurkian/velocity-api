package com.velocity.api.user.dto;

import com.velocity.api.bike.BikeStatus;
import com.velocity.api.bike.repository.projection.InstanceProjection;
import com.velocity.api.common.City;

import java.util.UUID;

public record BikeInstanceResponse(
        UUID id,
        BikeStatus status,
        City city,
        String bikeModelName,
        Long version
) {
    public static BikeInstanceResponse from(InstanceProjection bike) {
        return new BikeInstanceResponse(
                bike.getId(),
                bike.getStatus(),
                bike.getCity(),
                bike.getBikeModelName(),
                bike.getVersion()
        );
    }
}
