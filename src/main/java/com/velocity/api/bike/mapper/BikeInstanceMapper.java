package com.velocity.api.bike.mapper;

import com.velocity.api.bike.BikeInstance;
import com.velocity.api.bike.dto.BikeInstanceDto;
import org.springframework.stereotype.Component;

@Component
public class BikeInstanceMapper {

    public BikeInstanceDto toDto(BikeInstance bike) {
        return new BikeInstanceDto(
                bike.getId(),
                bike.getStatus(),
                bike.getCity(),
                bike.getBikeModel() != null ? bike.getBikeModel().getId() : null);
    }
}
