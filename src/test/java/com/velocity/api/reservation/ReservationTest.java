package com.velocity.api.reservation;

import com.velocity.api.common.exception.InvalidStatusTransitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

public class ReservationTest {

    @ParameterizedTest
    @CsvSource({
            "PENDING, CONFIRMED",
            "PENDING, CANCELLED",
            "CONFIRMED, COMPLETED",
            "CONFIRMED, CANCELLED"
    })
    @DisplayName("Valid state transitions should correctly update the reservation status")
    public void transitionTo_validStates_updatesStatus(ReservationStatus from, ReservationStatus to) {
        Reservation reservation = new Reservation();
        ReflectionTestUtils.setField(reservation, "status", from);

        reservation.transitionTo(to);

        assertEquals(to, reservation.getStatus());
    }

    @ParameterizedTest
    @CsvSource({
            "PENDING, COMPLETED",
            "CONFIRMED, PENDING",
            "CANCELLED, PENDING",
            "CANCELLED, CONFIRMED",
            "CANCELLED, COMPLETED",
            "COMPLETED, PENDING",
            "COMPLETED, CONFIRMED",
            "COMPLETED, CANCELLED"
    })
    @DisplayName("Invalid state transitions should throw exception containing from and to states")
    public void transitionTo_invalidStates_throwsInvalidStatusTransitionException(ReservationStatus from, ReservationStatus to) {
        Reservation reservation = new Reservation();
        ReflectionTestUtils.setField(reservation, "status", from);

        InvalidStatusTransitionException exception = assertThrows(InvalidStatusTransitionException.class, () -> {
            reservation.transitionTo(to);
        });

        assertTrue(exception.getMessage().contains(from.name()),
                "Message should contain FROM state: " + from);
        assertTrue(exception.getMessage().contains(to.name()),
                "Message should contain TO state: " + to);
    }

    @ParameterizedTest
    @CsvSource({
            "PENDING, PENDING",
            "CONFIRMED, CONFIRMED",
            "CANCELLED, CANCELLED",
            "COMPLETED, COMPLETED"
    })
    @DisplayName("Same-state transitions should act as a safe no-op")
    public void transitionTo_sameState_doesNothing(ReservationStatus from, ReservationStatus to) {
        Reservation reservation = new Reservation();
        ReflectionTestUtils.setField(reservation, "status", from);

        reservation.transitionTo(to);

        assertEquals(to, reservation.getStatus());
    }
}
