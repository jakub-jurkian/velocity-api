package com.velocity.api.bike.mapper;

import com.velocity.api.bike.BikeInstance;
import com.velocity.api.bike.dto.BikeInstanceDto;
import org.springframework.stereotype.Component;

@Component
public class BikeInstanceMapper {

    public BikeInstanceDto toDto(BikeInstance bike) {
        BikeInstanceDto dto = new BikeInstanceDto();
        dto.setId(bike.getId());
        dto.setCity(bike.getCity());
        dto.setStatus(bike.getStatus());
        if (bike.getBikeModel() != null) {
            dto.setBikeModelId(bike.getBikeModel().getId());
        }
        return dto;
    }
}
