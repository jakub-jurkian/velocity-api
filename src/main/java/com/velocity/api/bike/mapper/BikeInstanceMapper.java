package com.velocity.api.bike.mapper;

import com.velocity.api.bike.BikeInstance;
import com.velocity.api.bike.dto.BikeInstanceResponse;
import org.springframework.stereotype.Component;

@Component
public class BikeInstanceMapper {

    public BikeInstanceResponse toDto(BikeInstance bike) {
        return new BikeInstanceResponse(
                bike.getId(),
                bike.getStatus(),
                bike.getCity(),
                bike.getBikeModel() != null ? bike.getBikeModel().getId() : null);
    }
}
