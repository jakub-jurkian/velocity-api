package com.velocity.api.reservation.dto;

import java.time.LocalDate;
import java.util.UUID;

public record ConflictDto(UUID reservationId, String userEmail, LocalDate startDate, LocalDate endDate) {
}
