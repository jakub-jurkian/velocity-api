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
        /**
         * Null for a self-cancellation. Populated only when someone else ended
         * the booking — today that means an admin taking the bike off the road
         * — so the client can tell "you cancelled this" apart from "we did",
         * and explain why.
         */
        String cancellationReason,
        BikeSummary bike
) {
}
