package com.velocity.api.reservation.scheduler;

import com.velocity.api.BaseIntegrationTest;
import com.velocity.api.bike.BikeInstance;
import com.velocity.api.bike.repository.BikeInstanceRepository;
import com.velocity.api.bike.repository.BikeModelRepository;
import com.velocity.api.factory.TestDataFactory;
import com.velocity.api.reservation.Reservation;
import com.velocity.api.reservation.ReservationStatus;
import com.velocity.api.reservation.repository.ReservationRepository;
import com.velocity.api.user.User;
import com.velocity.api.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@Slf4j
@SpringBootTest
@ActiveProfiles("test")
@Import(TestDataFactory.class) // This explicitly pulls the factory into the test context
public class ReservationSchedulerIntegrationTest extends BaseIntegrationTest {
    @Autowired
    private ReservationLifecycleScheduler scheduler;
    @Autowired
    private ReservationRepository reservationRepository;
    @Autowired
    private BikeInstanceRepository bikeInstanceRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BikeModelRepository bikeModelRepository;

    @Autowired
    private TestDataFactory testDataFactory;

    @MockitoBean
    private Clock clock;

    @AfterEach
    public void cleanUp() {
        reservationRepository.deleteAll();
        bikeInstanceRepository.deleteAll();
        bikeModelRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    public void cancelStalePendingReservations_OlderThan30Minutes_changeReservationsStateToCancelled() {
        // Arrange: Create explicit test data to satisfy Foreign Key constraints

        when(clock.instant()).thenReturn(Instant.parse("2026-09-01T10:45:00Z"));
        when(clock.getZone()).thenReturn(ZoneId.of("UTC")); // Required for LocalDate.now(clock)

        User testUser = testDataFactory.createAndSaveDefaultUser();
        BikeInstance testBike = testDataFactory.createAndSaveDefaultBike();
        Reservation bookedNormalReservation = testDataFactory.createAndSaveReservation(
                testUser, testBike, LocalDate.parse("2026-09-05"), LocalDate.parse("2026-09-10"), LocalDate.now(clock)
        );
        when(clock.instant()).thenReturn(Instant.parse("2026-09-01T10:00:00Z"));
        Reservation bookedStaleReservation = testDataFactory.createAndSaveReservation(
                testUser, testBike, LocalDate.parse("2026-10-05"), LocalDate.parse("2026-10-10"), LocalDate.now(clock)
        );
        // Act
        when(clock.instant()).thenReturn(Instant.parse("2026-09-01T11:00:00Z"));
        scheduler.cancelStalePendingReservations();

        Reservation updatedNormalReservation = reservationRepository.findById(bookedNormalReservation.getId()).orElseThrow();
        Reservation updatedStaleReservation = reservationRepository.findById(bookedStaleReservation.getId()).orElseThrow();
        // Assert
        assertEquals(ReservationStatus.PENDING, updatedNormalReservation.getStatus());
        assertEquals(ReservationStatus.CANCELLED, updatedStaleReservation.getStatus());
    }

    @Test
    public void completePastDueReservations_EndDateInPast_UpdatesStatusToCompleted() {
        when(clock.instant()).thenReturn(Instant.parse("2026-09-01T00:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneId.of("UTC"));

        User testUser = testDataFactory.createAndSaveDefaultUser();
        BikeInstance testBike = testDataFactory.createAndSaveDefaultBike();
        Reservation bookedNormalReservation = testDataFactory.createAndSaveReservation(
                testUser, testBike, LocalDate.parse("2026-10-05"), LocalDate.parse("2026-10-10"), LocalDate.now(clock)
        );

        Reservation pastDueReservation = testDataFactory.createAndSaveReservation(
                testUser, testBike, LocalDate.parse("2026-09-05"), LocalDate.parse("2026-09-10"), LocalDate.now(clock)
        );
        pastDueReservation.transitionTo(ReservationStatus.CONFIRMED, LocalDate.now());
        Reservation bookedPastDueReservation = reservationRepository.save(pastDueReservation);
        // Act
        when(clock.instant()).thenReturn(Instant.parse("2026-09-20T00:00:00Z"));
        scheduler.completePastDueConfirmedReservations();

        Reservation updatedNormalReservation = reservationRepository.findById(bookedNormalReservation.getId()).orElseThrow();
        Reservation updatedPastDueReservation = reservationRepository.findById(bookedPastDueReservation.getId()).orElseThrow();
        // Assert
        assertEquals(ReservationStatus.PENDING, updatedNormalReservation.getStatus());
        assertEquals(ReservationStatus.COMPLETED, updatedPastDueReservation.getStatus());
    }
}
