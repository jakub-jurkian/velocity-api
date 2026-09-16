package com.velocity.api.reservation.dto;

import com.velocity.api.pricing.dto.RentalQuote;

import java.util.List;

public record AvailabilityResponse(
        RentalQuote quote,
        List<AvailableBikeModel> models
) {
}
