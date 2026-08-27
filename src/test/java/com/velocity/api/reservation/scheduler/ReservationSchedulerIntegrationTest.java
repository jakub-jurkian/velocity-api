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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TestDataFactory testDataFactory;

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

        User testUser = testDataFactory.createAndSaveDefaultUser();
        BikeInstance testBike = testDataFactory.createAndSaveDefaultBike();
        Reservation bookedNormalReservation = testDataFactory.createAndSaveReservation(
                testUser, testBike, LocalDate.parse("2026-09-05"), LocalDate.parse("2026-09-10")
        );

        Reservation bookedStaleReservation = testDataFactory.createAndSaveReservation(
                testUser, testBike, LocalDate.parse("2026-10-05"), LocalDate.parse("2026-10-10")
        );
        //The Database Backdoor: Force the timestamp 7 days into the past
        jdbcTemplate.update("UPDATE reservations SET created_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minus(7, ChronoUnit.DAYS)),
                bookedStaleReservation.getId());
        // Act
        scheduler.cancelStalePendingReservations();

        Reservation updatedNormalReservation = reservationRepository.findById(bookedNormalReservation.getId()).orElseThrow();
        Reservation updatedStaleReservation = reservationRepository.findById(bookedStaleReservation.getId()).orElseThrow();
        // Assert
        assertEquals(ReservationStatus.PENDING, updatedNormalReservation.getStatus());
        assertEquals(ReservationStatus.CANCELLED, updatedStaleReservation.getStatus());
    }

    @Test
    public void completePastDueReservations_EndDateInPast_UpdatesStatusToCompleted() {
        User testUser = testDataFactory.createAndSaveDefaultUser();
        BikeInstance testBike = testDataFactory.createAndSaveDefaultBike();
        Reservation bookedNormalReservation = testDataFactory.createAndSaveReservation(
                testUser, testBike, LocalDate.parse("2026-09-05"), LocalDate.parse("2026-09-10")
        );

        Reservation pastDueReservation = testDataFactory.createAndSaveReservation(
                testUser, testBike, LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-08")
        );
        pastDueReservation.transitionTo(ReservationStatus.CONFIRMED);
        Reservation bookedPastDueReservation = reservationRepository.save(pastDueReservation);
        // Act
        scheduler.completePastDueConfirmedReservations();

        Reservation updatedNormalReservation = reservationRepository.findById(bookedNormalReservation.getId()).orElseThrow();
        Reservation updatedPastDueReservation = reservationRepository.findById(bookedPastDueReservation.getId()).orElseThrow();
        // Assert
        assertEquals(ReservationStatus.PENDING, updatedNormalReservation.getStatus());
        assertEquals(ReservationStatus.COMPLETED, updatedPastDueReservation.getStatus());
    }
}
