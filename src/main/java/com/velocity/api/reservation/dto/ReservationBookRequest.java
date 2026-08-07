package com.velocity.api.reservation.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record ReservationBookRequest(
        @NotNull(message = "Bike instance ID is required")
        UUID bikeInstanceId,
        @NotNull(message = "Start date is required")
        @FutureOrPresent(message = "Start date cannot be in the past")
        LocalDate startDate,
        @NotNull(message = "End date is required")
        @Future(message = "End date must be in the future")
        LocalDate endDate) {
    @AssertTrue(message = "End date must be strictly after start date")
    // if false, validator immediately halts execution and throws a MethodArgumentNotValidException.
    public boolean isValidDateRange() {
        if (startDate == null || endDate == null) return true;
        return endDate.isAfter(startDate);
    }
}
