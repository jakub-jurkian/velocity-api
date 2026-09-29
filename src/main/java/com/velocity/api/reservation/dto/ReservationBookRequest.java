package com.velocity.api.reservation.dto;

import com.velocity.api.reservation.RentalPeriod;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record ReservationBookRequest(
        @NotNull(message = "Bike instance ID is required")
        UUID bikeInstanceId,
        @NotNull(message = "Start date is required")
        @Future(message = "Start date cannot be in the past or present")
        LocalDate startDate,
        @NotNull(message = "End date is required")
        LocalDate endDate) {

    // The rules live in RentalPeriod; these only report them per field (400) before the
    // service builds one.
    @AssertTrue(message = "End date must be strictly after start date")
    public boolean isValidDateOrder() {
        if (startDate == null || endDate == null) return true;
        return RentalPeriod.isInOrder(startDate, endDate);
    }

    @AssertTrue(message = "Rental duration must be between " + RentalPeriod.MIN_DAYS + " and " + RentalPeriod.MAX_DAYS + " days")
    public boolean isDurationValid() {
        if (startDate == null || endDate == null) return true;
        return RentalPeriod.hasAllowedLength(startDate, endDate);
    }

    public RentalPeriod period() {
        return new RentalPeriod(startDate, endDate);
    }
}
