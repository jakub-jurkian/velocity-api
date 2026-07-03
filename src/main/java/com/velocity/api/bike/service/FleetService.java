package com.velocity.api.bike.service;

import com.velocity.api.bike.BikeInstance;
import com.velocity.api.bike.BikeStatus;
import com.velocity.api.bike.dto.BikeInstanceDto;
import com.velocity.api.bike.repository.BikeInstanceRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FleetService {
    private final BikeInstanceRepository bikeInstanceRepository;

    public FleetService(BikeInstanceRepository bikeInstanceRepository) {
        this.bikeInstanceRepository = bikeInstanceRepository;
    }

    private BikeInstanceDto mapToDto(BikeInstance bike) {
        BikeInstanceDto dto = new BikeInstanceDto();
        dto.setId(bike.getId());
        dto.setCity(bike.getCity());
        dto.setStatus(bike.getStatus());
        if (bike.getBikeModel() != null) {
            dto.setBikeModelId(bike.getBikeModel().getId());
        }
        return dto;
    }

    public List<BikeInstanceDto> getAvailableBikes() {
        return bikeInstanceRepository.findAll().stream()
                .filter(bike -> bike.getStatus() == BikeStatus.ACTIVE)
                .map(this::mapToDto)
                .toList();
    }
}
