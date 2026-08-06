package com.velocity.api.bike.repository.projection;

import com.velocity.api.bike.BikeCategory;

import java.util.UUID;

public interface AvailableModelProjection {
    UUID getBookableInstanceId();
    String getModelName();
    String getModelDescription();
    int getModelSpeed();
    int getModelRange();
    int getModelCapacity();
    BikeCategory getModelCategory();
}
