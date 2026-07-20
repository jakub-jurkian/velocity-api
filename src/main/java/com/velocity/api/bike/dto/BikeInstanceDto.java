package com.velocity.api.bike.dto;

import com.velocity.api.bike.BikeStatus;
import com.velocity.api.common.City;

import java.util.UUID;

public record BikeInstanceDto(
        UUID id,
        BikeStatus status,
        City city,
        UUID bikeModelId
) {}
