package com.velocity.api.reservation.dto;

import com.velocity.api.reservation.Reservation;
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
    public static ReservationBookResponse from(Reservation reservation, BikeSummary bikeSummary) {
        return new ReservationBookResponse(
                reservation.getId(),
                reservation.getStartDate(),
                reservation.getEndDate(),
                reservation.getTotalCost(),
                reservation.getStatus(),
                reservation.getCreatedAt(),
                bikeSummary
        );
    }
}
