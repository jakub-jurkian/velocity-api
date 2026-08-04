package com.velocity.api.reservation;

import com.velocity.api.bike.BikeCategory;
import com.velocity.api.bike.BikeInstance;
import com.velocity.api.bike.BikeModel;
import com.velocity.api.bike.repository.BikeInstanceRepository;
import com.velocity.api.bike.repository.BikeModelRepository;
import com.velocity.api.reservation.dto.ReservationCreateRequest;
import com.velocity.api.reservation.repository.ReservationRepository;
import com.velocity.api.common.City;
import com.velocity.api.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

// default - webEnvironment = WebEnvironment.MOCK - does not start real web server (creates mock servlet env in memory)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureTestRestTemplate
public class ReservationConcurrencyIntegrationTest {
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private TestRestTemplate testRestTemplate;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BikeModelRepository bikeModelRepository;
    @Autowired
    private BikeInstanceRepository bikeInstanceRepository;
    @Autowired
    private ReservationRepository reservationRepository;

    private UUID bikeInstanceId;

    @BeforeEach
    public void setUp() {
        jdbcTemplate.update(
                "INSERT INTO users (id, email, password_hash, full_name, phone, status, role, city, joined_date) " +
                        "VALUES ('00000000-0000-0000-0000-000000000001', 'test@test.com', 'hash', 'Test', '123', 'ACTIVE', 'CLIENT', 'WARSAW', now())"
        );
        // force the hardcoded user into DB cause id is hardcoded in controller right now
        // User savedUser = userRepository.save(new User("test@test.com", "xf843fd23iom4r", "Test", "+48000000000", UserRole.CLIENT, City.GDANSK));
        BikeModel savedBikeModel = bikeModelRepository.save(new BikeModel("Test", "Test", 100, 100, 50, BikeCategory.AGILITY));
        BikeInstance savedBikeInstance = bikeInstanceRepository.save(new BikeInstance(savedBikeModel, City.GDANSK));
        bikeInstanceId = savedBikeInstance.getId();
    }

    @AfterEach
    public void cleanUp() {
        reservationRepository.deleteAll();
        bikeInstanceRepository.deleteAll();
        bikeModelRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    public void shouldReturnConflictOnConcurrentOverlappingReservations() throws ExecutionException, InterruptedException {
        // prep the req
        ReservationCreateRequest req = new ReservationCreateRequest(bikeInstanceId, LocalDate.parse("2026-09-05"), LocalDate.parse("2026-09-10"));

        // init concurrency tools
        CountDownLatch latch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        // Define the work to be done by the threads
        Callable<ResponseEntity<String>> bookingTask = () -> {
            // Freeze the thread until the main test thread drops the gate
            latch.await();
            // Fire the POST request to the controller
            return testRestTemplate.postForEntity("/api/v1/reservations", req, String.class);
        };

        try {
            // Hand the identical task to the executor twice
            Future<ResponseEntity<String>> task1 = executor.submit(bookingTask);
            Future<ResponseEntity<String>> task2 = executor.submit(bookingTask);

            latch.countDown();

            ResponseEntity<String> res1 = task1.get();
            ResponseEntity<String> res2 = task2.get();
            HttpStatus status1 = (HttpStatus) res1.getStatusCode();
            HttpStatus status2 = (HttpStatus) res2.getStatusCode();
            System.out.println("Response 1 Body: " + res1.getBody());
            System.out.println("Response 2 Body: " + res2.getBody());
            // assert that one is 201 and second one is 409
            List<HttpStatus> statuses = List.of(status1, status2);
            assertThat(statuses).containsExactlyInAnyOrder(HttpStatus.CREATED, HttpStatus.CONFLICT);
        } finally {
            executor.shutdown();
        }
    }
}
