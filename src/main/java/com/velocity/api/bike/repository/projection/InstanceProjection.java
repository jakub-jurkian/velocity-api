package com.velocity.api.bike.repository.projection;

import com.velocity.api.bike.BikeStatus;
import com.velocity.api.common.City;

import java.util.UUID;

public interface InstanceProjection {
    UUID getId();
    City getCity();
    BikeStatus getStatus();
    String getBikeModelName();
    Long getVersion();
}
