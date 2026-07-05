package com.velocity.api.bike.service;

import com.velocity.api.bike.BikeStatus;
import com.velocity.api.bike.dto.BikeInstanceDto;
import com.velocity.api.bike.mapper.BikeInstanceMapper;
import com.velocity.api.bike.repository.BikeInstanceRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class FleetService {
    private final BikeInstanceRepository bikeInstanceRepository;
    private final BikeInstanceMapper bikeInstanceMapper;

    public FleetService(BikeInstanceRepository bikeInstanceRepository, BikeInstanceMapper bikeInstanceMapper) {
        this.bikeInstanceRepository = bikeInstanceRepository;
        this.bikeInstanceMapper = bikeInstanceMapper;
    }

    public Page<BikeInstanceDto> getAvailableBikes(Pageable pageable) {
        return bikeInstanceRepository.findByStatus(BikeStatus.ACTIVE, pageable)
                .map(bikeInstanceMapper::toDto);
    }
}
