package com.velocity.api.reservation;

import com.velocity.api.AbstractApiIntegrationTest;
import com.velocity.api.bike.BikeInstance;
import com.velocity.api.common.City;
import com.velocity.api.common.exception.ResourceNotFoundException;
import com.velocity.api.factory.TestDataFactory;
import com.velocity.api.reservation.repository.ReservationRepository;
import com.velocity.api.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.UUID;

import static com.velocity.api.security.SecurityTestHelper.asUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ReservationIntegrationTest extends AbstractApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestDataFactory testDataFactory;

    @Autowired
    private ReservationRepository reservationRepository;

    @Test
    public void getMyReservations_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/reservations/my")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void getMyReservations_AuthenticatedUser_ReturnsOnlyTheirReservations() throws Exception {
        clock.setInstant("2026-09-01T10:00:00Z");
        User primaryUser = testDataFactory.createAndSaveDefaultUser();
        // A second user proves the query does not leak their data
        User otherUser = testDataFactory.createAndSaveUser("other@test.com", "123456789");

        BikeInstance bike = testDataFactory.createAndSaveDefaultBike();

        testDataFactory.createAndSaveReservation(
                primaryUser, bike, LocalDate.parse("2026-09-05"), LocalDate.parse("2026-09-10"), LocalDate.now(clock)
        );
        testDataFactory.createAndSaveReservation(
                primaryUser, bike, LocalDate.parse("2026-09-10"), LocalDate.parse("2026-09-15"), LocalDate.now(clock)
        );
        // Bounds that don't violate the ADR-001 exclusion constraint
        testDataFactory.createAndSaveReservation(
                otherUser, bike, LocalDate.parse("2026-09-20"), LocalDate.parse("2026-09-25"), LocalDate.now(clock)
        );

        mockMvc.perform(get("/api/v1/reservations/my?page=0&size=10")
                        .with(asUser(String.valueOf(primaryUser.getId()), "CLIENT"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.totalElements").value(2))
                .andExpect(jsonPath("$.meta.totalPages").value(1))
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    public void book_bikeInAnotherCity_isRefused() throws Exception {
        User warsawUser = testDataFactory.createAndSaveDefaultUser();
        BikeInstance gdanskBike = testDataFactory.createAndSaveBike(City.GDANSK);
        LocalDate start = LocalDate.now(clock).plusDays(5);

        mockMvc.perform(post("/api/v1/reservations")
                        .with(asUser(warsawUser.getId().toString(), "CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bikeInstanceId": "%s", "startDate": "%s", "endDate": "%s"}
                                """.formatted(gdanskBike.getId(), start, start.plusDays(5))))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.title").value("Bike In Another City"));

        assertThat(reservationRepository.count()).isZero();
    }

    @Test
    public void confirmReservation_AuthorizedUser_Success() throws Exception {
        clock.setInstant("2026-09-01T10:00:00Z");
        User primaryUser = testDataFactory.createAndSaveDefaultUser();
        BikeInstance bike = testDataFactory.createAndSaveDefaultBike();

        Reservation reservation = testDataFactory.createAndSaveReservation(
                primaryUser, bike, LocalDate.parse("2026-09-05"), LocalDate.parse("2026-09-10"), LocalDate.now(clock)
        );

        UUID reservationId = reservation.getId();

        mockMvc.perform(post("/api/v1/reservations/%s/confirm".formatted(reservationId))
                        .with(asUser(String.valueOf(primaryUser.getId()), "CLIENT"))
                )
                .andExpect(status().isNoContent());
        Reservation freshReservation = reservationRepository.findById(reservationId).orElseThrow(() ->
                new ResourceNotFoundException("Reservation not found."));
        assertThat(freshReservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    public void confirmReservation_afterConfirmationWindow_isRefused() throws Exception {
        clock.setInstant("2026-09-01T10:00:00Z");
        User user = testDataFactory.createAndSaveDefaultUser();
        BikeInstance bike = testDataFactory.createAndSaveDefaultBike();
        Reservation reservation = testDataFactory.createAndSaveReservation(
                user, bike, LocalDate.parse("2026-09-05"), LocalDate.parse("2026-09-10"), LocalDate.now(clock)
        );

        // 31 minutes later, before the scheduler has swept it
        clock.setInstant("2026-09-01T10:31:00Z");
        mockMvc.perform(post("/api/v1/reservations/%s/confirm".formatted(reservation.getId()))
                        .with(asUser(user.getId().toString(), "CLIENT")))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.title").value("Reservation Expired"));

        assertThat(reservationRepository.findById(reservation.getId()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.PENDING);
    }

    @Test
    public void confirmReservation_reservationOfAnotherUser_returnsNotFound() throws Exception {
        clock.setInstant("2026-09-01T10:00:00Z");
        User primaryUser = testDataFactory.createAndSaveDefaultUser();
        User otherUser = testDataFactory.createAndSaveUser("other@test.com", "123456789");
        BikeInstance bike = testDataFactory.createAndSaveDefaultBike();

        Reservation reservation = testDataFactory.createAndSaveReservation(
                primaryUser, bike, LocalDate.parse("2026-09-05"), LocalDate.parse("2026-09-10"), LocalDate.now(clock)
        );

        UUID reservationId = reservation.getId();

        mockMvc.perform(post("/api/v1/reservations/%s/confirm".formatted(reservationId))
                        .with(asUser(String.valueOf(otherUser.getId()), "CLIENT"))
                )
                // A reservation the caller does not own is indistinguishable from
                // one that does not exist, so the row's existence is never confirmed.
                .andExpect(status().isNotFound());

        Reservation freshReservation = reservationRepository.findById(reservationId).orElseThrow(() ->
                new ResourceNotFoundException("Reservation not found."));
        assertThat(freshReservation.getStatus()).isEqualTo(ReservationStatus.PENDING);
    }
}
