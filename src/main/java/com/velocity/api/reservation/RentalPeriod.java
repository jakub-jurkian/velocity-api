package com.velocity.api.reservation;

import com.velocity.api.common.exception.DomainValidationException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * The dates a bike is rented for. {@code endDate} is exclusive, matching both the pricing
 * ({@code days = endDate - startDate}) and the {@code [)} bounds of the exclusion constraint
 * in ADR-001, so back-to-back rentals do not collide.
 *
 * <p>The single home of the 3-21 day rule: the booking request, the availability endpoint and
 * the {@link Reservation} entity all go through this type instead of repeating the numbers.
 */
public record RentalPeriod(LocalDate startDate, LocalDate endDate) {
    public static final int MIN_DAYS = 3;
    public static final int MAX_DAYS = 21;

    public RentalPeriod {
        if (startDate == null) throw new DomainValidationException("Start date is required.");
        if (endDate == null) throw new DomainValidationException("End date is required.");
        if (!isInOrder(startDate, endDate)) {
            throw new DomainValidationException("End date must be after start date.");
        }
        if (!hasAllowedLength(startDate, endDate)) {
            throw new DomainValidationException(
                    "Rental duration must be between " + MIN_DAYS + " and " + MAX_DAYS + " days.");
        }
    }

    public int days() {
        return Math.toIntExact(ChronoUnit.DAYS.between(startDate, endDate));
    }

    // The two checks below also back the Bean Validation constraints on the booking request,
    // which reports them per field (400) before a RentalPeriod is ever built.
    public static boolean isInOrder(LocalDate startDate, LocalDate endDate) {
        return endDate.isAfter(startDate);
    }

    public static boolean hasAllowedLength(LocalDate startDate, LocalDate endDate) {
        long days = ChronoUnit.DAYS.between(startDate, endDate);
        return days >= MIN_DAYS && days <= MAX_DAYS;
    }
}
