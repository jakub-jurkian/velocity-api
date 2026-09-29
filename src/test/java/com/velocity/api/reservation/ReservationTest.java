package com.velocity.api.reservation;

import com.velocity.api.common.exception.DomainValidationException;
import com.velocity.api.reservation.exception.InvalidStatusTransitionException;
import com.velocity.api.reservation.exception.ReservationExpiredException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;

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
        ReflectionTestUtils.setField(reservation, "startDate", LocalDate.now().plusDays(1));

        reservation.transitionTo(to, LocalDate.now());

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

        InvalidStatusTransitionException exception = assertThrows(InvalidStatusTransitionException.class, () -> reservation.transitionTo(to, LocalDate.now()));

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

        reservation.transitionTo(to, LocalDate.now());

        assertEquals(to, reservation.getStatus());
    }

    @Test
    @DisplayName("confirm() within the 30-minute window confirms the reservation")
    public void confirm_withinWindow_confirms() {
        Reservation reservation = pendingCreatedAt("2026-09-01T10:00:00Z");

        reservation.confirm(Instant.parse("2026-09-01T10:29:59Z"));

        assertEquals(ReservationStatus.CONFIRMED, reservation.getStatus());
    }

    @Test
    @DisplayName("confirm() once the window has passed is refused and leaves the reservation PENDING")
    public void confirm_afterWindow_throwsReservationExpiredException() {
        Reservation reservation = pendingCreatedAt("2026-09-01T10:00:00Z");

        assertThrows(ReservationExpiredException.class, () -> reservation.confirm(Instant.parse("2026-09-01T10:30:00Z")));
        assertEquals(ReservationStatus.PENDING, reservation.getStatus());
    }

    @Test
    @DisplayName("expire() cancels a stale reservation even on its start date and records why")
    public void expire_onStartDate_cancelsWithReason() {
        Reservation reservation = pendingCreatedAt("2026-09-04T21:50:00Z");
        ReflectionTestUtils.setField(reservation, "startDate", LocalDate.parse("2026-09-05"));

        // 00:20 on 5 September in Warsaw - the customer's late-cancel rule would refuse this
        reservation.expire(Instant.parse("2026-09-04T22:20:00Z"));

        assertEquals(ReservationStatus.CANCELLED, reservation.getStatus());
        assertEquals("Not confirmed within 30 minutes", reservation.getCancellationReason());
    }

    @Test
    @DisplayName("expire() inside the window is refused")
    public void expire_withinWindow_throwsDomainValidationException() {
        Reservation reservation = pendingCreatedAt("2026-09-01T10:00:00Z");

        assertThrows(DomainValidationException.class, () -> reservation.expire(Instant.parse("2026-09-01T10:10:00Z")));
        assertEquals(ReservationStatus.PENDING, reservation.getStatus());
    }

    @ParameterizedTest
    @CsvSource({"CONFIRMED", "CANCELLED", "COMPLETED"})
    @DisplayName("expire() is a no-op once the reservation has left PENDING")
    public void expire_notPending_doesNothing(ReservationStatus status) {
        Reservation reservation = pendingCreatedAt("2026-09-01T10:00:00Z");
        ReflectionTestUtils.setField(reservation, "status", status);

        reservation.expire(Instant.parse("2026-09-01T12:00:00Z"));

        assertEquals(status, reservation.getStatus());
    }

    private static Reservation pendingCreatedAt(String isoInstant) {
        Reservation reservation = new Reservation();
        ReflectionTestUtils.setField(reservation, "status", ReservationStatus.PENDING);
        ReflectionTestUtils.setField(reservation, "createdAt", Instant.parse(isoInstant));
        return reservation;
    }
}
