package com.velocity.api.bike.service;

import com.velocity.api.bike.BikeStatus;
import com.velocity.api.bike.dto.BikeInstanceCountResponse;
import com.velocity.api.bike.repository.BikeInstanceRepository;
import com.velocity.api.common.City;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FleetService {
    private final BikeInstanceRepository bikeInstanceRepository;

    public BikeInstanceCountResponse getActiveBikesCount(City city) {
        long count = bikeInstanceRepository.countByStatusAndCity(BikeStatus.ACTIVE, city);
        return new BikeInstanceCountResponse(count);
    }
}
