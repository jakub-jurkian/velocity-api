package com.velocity.api.reservation.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ReservationDto(
        UUID id,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal totalCost,
        String status,
        UUID userId,
        UUID bikeInstanceId) {
}
