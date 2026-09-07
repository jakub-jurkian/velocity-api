package com.velocity.api.bike.service;

import com.velocity.api.bike.BikeStatus;
import com.velocity.api.bike.dto.BikeInstanceCountResponse;
import com.velocity.api.bike.repository.BikeInstanceRepository;
import com.velocity.api.common.City;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FleetService {
    private final BikeInstanceRepository bikeInstanceRepository;

    @Transactional(readOnly = true)
    public BikeInstanceCountResponse getBikesCount(City city, BikeStatus status) {
        long count = bikeInstanceRepository.countByStatusAndCity(status, city);
        return new BikeInstanceCountResponse(count);
    }
}
