package com.velocity.api.reservation.dto;

import com.velocity.api.common.City;
import com.velocity.api.reservation.ReservationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ReservationBookResponse(
        UUID id,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal totalCost,
        ReservationStatus status,
        Instant createdAt,
        BikeSummary bike
) {
    public record BikeSummary(UUID id, String modelName, City city) {
    }
}
