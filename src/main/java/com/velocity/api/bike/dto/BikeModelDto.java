package com.velocity.api.bike.dto;

import com.velocity.api.bike.BikeCategory;

import java.util.UUID;

public record BikeModelDto(
        UUID id,
        String name,
        String description,
        int speed,
        int range,
        int capacity,
        BikeCategory category
) {}
