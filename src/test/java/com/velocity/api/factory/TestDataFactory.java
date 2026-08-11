package com.velocity.api.factory;

import com.velocity.api.bike.BikeCategory;
import com.velocity.api.bike.BikeInstance;
import com.velocity.api.bike.BikeModel;
import com.velocity.api.bike.repository.BikeInstanceRepository;
import com.velocity.api.bike.repository.BikeModelRepository;
import com.velocity.api.common.City;
import com.velocity.api.reservation.Reservation;
import com.velocity.api.reservation.repository.ReservationRepository;
import com.velocity.api.user.User;
import com.velocity.api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.test.context.TestComponent;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@TestComponent
@RequiredArgsConstructor
public class TestDataFactory {
    // Inject repositories via constructor
    private final UserRepository userRepository;
    private final BikeModelRepository bikeModelRepository;
    private final BikeInstanceRepository bikeInstanceRepository;
    private final ReservationRepository reservationRepository;

    // Create reusable methods
    public User createAndSaveDefaultUser() {
        User testUser = User.registerClient(
                "test.user@velocity.com",
                "hashed_pw",
                "Integration Test User",
                "+48123456789",
                City.WARSAW
        );
        return userRepository.save(testUser);
    }

    public BikeInstance createAndSaveDefaultBike() {
        BikeModel testModel = BikeModel.create(
                "Integration Test Model " + UUID.randomUUID(),
                "Test description",
                45,
                80,
                40,
                BikeCategory.AGILITY
        );
        bikeModelRepository.save(testModel);

        BikeInstance testBike = BikeInstance.initialize(testModel, City.WARSAW);
        return bikeInstanceRepository.save(testBike);
    }

    public Reservation createAndSaveReservation(User user, BikeInstance bikeInstance, LocalDate startDate, LocalDate endDate) {
        Reservation normalReservation = Reservation.book(
                user,
                bikeInstance,
                startDate,
                endDate,
                BigDecimal.ONE
        );
        return reservationRepository.save(normalReservation);
    }
}
