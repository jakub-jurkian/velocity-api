package com.velocity.api.bike.service;

import com.velocity.api.bike.BikeStatus;
import com.velocity.api.bike.dto.BikeInstanceDto;
import com.velocity.api.bike.mapper.BikeInstanceMapper;
import com.velocity.api.bike.repository.BikeInstanceRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FleetService {
    private final BikeInstanceRepository bikeInstanceRepository;
    private final BikeInstanceMapper bikeInstanceMapper;

    public FleetService(BikeInstanceRepository bikeInstanceRepository, BikeInstanceMapper bikeInstanceMapper) {
        this.bikeInstanceRepository = bikeInstanceRepository;
        this.bikeInstanceMapper = bikeInstanceMapper;
    }

    public List<BikeInstanceDto> getAvailableBikes() {
        return bikeInstanceRepository.findByStatus(BikeStatus.ACTIVE).stream()
                .map(bikeInstanceMapper::toDto)
                .toList();
    }
}
