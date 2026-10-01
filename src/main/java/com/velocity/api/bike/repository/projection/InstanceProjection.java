package com.velocity.api.bike.repository.projection;

import com.velocity.api.bike.BikeStatus;
import com.velocity.api.common.City;

import java.util.UUID;

public interface InstanceProjection {
    UUID getId();
    City getCity();
    BikeStatus getStatus();
    ModelName getBikeModel();
    Long getVersion();

    // Nested projection: only the model's name is read, not the whole BikeModel
    interface ModelName {
        String getName();
    }
}
