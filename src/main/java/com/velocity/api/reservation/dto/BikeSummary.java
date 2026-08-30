package com.velocity.api.reservation.dto;

import com.velocity.api.common.City;

import java.util.UUID;

public record BikeSummary(UUID id, String modelName, City city) {
}