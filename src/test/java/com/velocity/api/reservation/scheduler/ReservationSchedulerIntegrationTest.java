package com.velocity.api.reservation.scheduler;

import com.velocity.api.AbstractApiIntegrationTest;
import com.velocity.api.bike.BikeInstance;
import com.velocity.api.factory.TestDataFactory;
import com.velocity.api.reservation.Reservation;
import com.velocity.api.reservation.ReservationStatus;
import com.velocity.api.reservation.repository.ReservationRepository;
import com.velocity.api.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ReservationSchedulerIntegrationTest extends AbstractApiIntegrationTest {
    @Autowired
    private ReservationLifecycleScheduler scheduler;
    @Autowired
    private ReservationRepository reservationRepository;
    @Autowired
    private TestDataFactory testDataFactory;

    @Test
    public void cancelStalePendingReservations_OlderThan30Minutes_changeReservationsStateToCancelled() {
        User testUser = testDataFactory.createAndSaveDefaultUser();
        BikeInstance testBike = testDataFactory.createAndSaveDefaultBike();

        clock.setInstant("2026-09-01T10:45:00Z");
        Reservation bookedNormalReservation = testDataFactory.createAndSaveReservation(
                testUser, testBike, LocalDate.parse("2026-09-05"), LocalDate.parse("2026-09-10"), LocalDate.now(clock)
        );
        clock.setInstant("2026-09-01T10:00:00Z");
        Reservation bookedStaleReservation = testDataFactory.createAndSaveReservation(
                testUser, testBike, LocalDate.parse("2026-10-05"), LocalDate.parse("2026-10-10"), LocalDate.now(clock)
        );

        clock.setInstant("2026-09-01T11:00:00Z");
        scheduler.cancelStalePendingReservations();

        Reservation updatedNormalReservation = reservationRepository.findById(bookedNormalReservation.getId()).orElseThrow();
        Reservation updatedStaleReservation = reservationRepository.findById(bookedStaleReservation.getId()).orElseThrow();
        assertEquals(ReservationStatus.PENDING, updatedNormalReservation.getStatus());
        assertEquals(ReservationStatus.CANCELLED, updatedStaleReservation.getStatus());
        assertThat(updatedStaleReservation.getCancellationReason()).isEqualTo("Not confirmed within 30 minutes");
    }

    @Test
    public void cancelStalePendingReservations_goesStaleAfterMidnightOnItsStartDate_isStillCancelled() {
        User testUser = testDataFactory.createAndSaveDefaultUser();
        BikeInstance testBike = testDataFactory.createAndSaveDefaultBike();

        // 23:50 in Warsaw on 4 September: booked for the next day
        clock.setInstant("2026-09-04T21:50:00Z");
        Reservation lateEveningBooking = testDataFactory.createAndSaveReservation(
                testUser, testBike, LocalDate.parse("2026-09-05"), LocalDate.parse("2026-09-10"), LocalDate.now(clock)
        );

        // 00:20 in Warsaw: stale, and its start date is already today. The customer's
        // late-cancel rule must not keep it PENDING (and blocking the bike) forever.
        clock.setInstant("2026-09-04T22:20:00Z");
        scheduler.cancelStalePendingReservations();

        Reservation updated = reservationRepository.findById(lateEveningBooking.getId()).orElseThrow();
        assertEquals(ReservationStatus.CANCELLED, updated.getStatus());
    }

    @Test
    public void completePastDueReservations_EndDateInPast_UpdatesStatusToCompleted() {
        clock.setInstant("2026-09-01T00:00:00Z");

        User testUser = testDataFactory.createAndSaveDefaultUser();
        BikeInstance testBike = testDataFactory.createAndSaveDefaultBike();
        Reservation bookedNormalReservation = testDataFactory.createAndSaveReservation(
                testUser, testBike, LocalDate.parse("2026-10-05"), LocalDate.parse("2026-10-10"), LocalDate.now(clock)
        );

        Reservation pastDueReservation = testDataFactory.createAndSaveReservation(
                testUser, testBike, LocalDate.parse("2026-09-05"), LocalDate.parse("2026-09-10"), LocalDate.now(clock)
        );
        pastDueReservation.confirm(clock.instant());
        Reservation bookedPastDueReservation = reservationRepository.save(pastDueReservation);

        clock.setInstant("2026-09-20T00:00:00Z");
        scheduler.completePastDueConfirmedReservations();

        Reservation updatedNormalReservation = reservationRepository.findById(bookedNormalReservation.getId()).orElseThrow();
        Reservation updatedPastDueReservation = reservationRepository.findById(bookedPastDueReservation.getId()).orElseThrow();
        assertEquals(ReservationStatus.PENDING, updatedNormalReservation.getStatus());
        assertEquals(ReservationStatus.COMPLETED, updatedPastDueReservation.getStatus());
    }
}
