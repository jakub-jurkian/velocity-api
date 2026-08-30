package com.velocity.api.reservation.dto;

import com.velocity.api.reservation.ReservationStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ReservationResponse(
        UUID id,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal totalCost,
        ReservationStatus status,
        BikeSummary bike
) {
}
