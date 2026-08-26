package com.velocity.api.bike.service;

import com.velocity.api.bike.BikeStatus;
import com.velocity.api.bike.dto.BikeInstanceResponse;
import com.velocity.api.bike.mapper.BikeInstanceMapper;
import com.velocity.api.bike.repository.BikeInstanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FleetService {
    private final BikeInstanceRepository bikeInstanceRepository;
    private final BikeInstanceMapper bikeInstanceMapper;

    public Page<BikeInstanceResponse> getActiveBikes(Pageable pageable) {
        return bikeInstanceRepository.findByStatus(BikeStatus.ACTIVE, pageable)
                .map(bikeInstanceMapper::toDto);
    }
}
