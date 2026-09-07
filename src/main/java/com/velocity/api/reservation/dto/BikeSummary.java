package com.velocity.api.reservation.dto;

import com.velocity.api.bike.BikeInstance;
import com.velocity.api.common.City;

import java.util.UUID;

public record BikeSummary(UUID id, String modelName, City city) {
    public static BikeSummary from(BikeInstance bikeInstance) {
        return new BikeSummary(bikeInstance.getId(), bikeInstance.getBikeModel().getName(), bikeInstance.getCity());
    }
}