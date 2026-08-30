package com.velocity.api.reservation;

import com.velocity.api.BaseIntegrationTest;
import com.velocity.api.bike.BikeInstance;
import com.velocity.api.bike.repository.BikeInstanceRepository;
import com.velocity.api.bike.repository.BikeModelRepository;
import com.velocity.api.factory.TestDataFactory;
import com.velocity.api.reservation.repository.ReservationRepository;
import com.velocity.api.user.User;
import com.velocity.api.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static com.velocity.api.security.SecurityTestHelper.asUser;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(TestDataFactory.class)
public class ReservationIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestDataFactory testDataFactory;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private BikeInstanceRepository bikeInstanceRepository;

    @Autowired
    private BikeModelRepository bikeModelRepository;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    public void cleanUp() {
        reservationRepository.deleteAll();
        bikeInstanceRepository.deleteAll();
        bikeModelRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    public void getMyReservations_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/reservations/my")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    public void getMyReservations_AuthenticatedUser_ReturnsOnlyTheirReservations() throws Exception {
        // Arrange Setup Database State
        User primaryUser = testDataFactory.createAndSaveDefaultUser();
        // Create a second user to prove we don't leak their data
        User otherUser = testDataFactory.createAndSaveUser("other@test.com", "123456789");

        BikeInstance bike = testDataFactory.createAndSaveDefaultBike();

        // Primary User makes 2 bookings
        testDataFactory.createAndSaveReservation(
                primaryUser, bike, LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-05")
        );
        testDataFactory.createAndSaveReservation(
                primaryUser, bike, LocalDate.parse("2026-09-10"), LocalDate.parse("2026-09-15")
        );

        // Other User makes 1 booking (using bounds that don't violate our ADR-001 exclusion constraint)
        testDataFactory.createAndSaveReservation(
                otherUser, bike, LocalDate.parse("2026-09-20"), LocalDate.parse("2026-09-25")
        );

        // Act Perform GET request as primaryUser
        mockMvc.perform(get("/api/v1/reservations/my?page=0&size=10")
                        .with(asUser(String.valueOf(primaryUser.getId()), "CLIENT"))
                        .accept(MediaType.APPLICATION_JSON))
                // Assert
                .andExpect(status().isOk())
                // Verify pagination metadata proves filtering worked at the database level
                .andExpect(jsonPath("$.meta.totalElements").value(2))
                .andExpect(jsonPath("$.meta.totalPages").value(1))
                // Verify the array contains exactly 2 elements
                .andExpect(jsonPath("$.data.length()").value(2));
    }
}