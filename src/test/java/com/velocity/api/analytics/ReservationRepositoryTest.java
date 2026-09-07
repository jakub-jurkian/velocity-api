package com.velocity.api.analytics;

import com.velocity.api.BaseIntegrationTest;
import com.velocity.api.bike.BikeCategory;
import com.velocity.api.bike.BikeInstance;
import com.velocity.api.bike.BikeModel;
import com.velocity.api.common.City;
import com.velocity.api.config.ClockConfig;
import com.velocity.api.reservation.Reservation;
import com.velocity.api.reservation.ReservationStatus;
import com.velocity.api.reservation.repository.ReservationRepository;
import com.velocity.api.reservation.repository.projection.FleetPopularityProjection;
import com.velocity.api.reservation.repository.projection.RevenueByMonthProjection;
import com.velocity.api.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(ClockConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class ReservationRepositoryTest extends BaseIntegrationTest {
    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private Clock clock;

    @BeforeEach
    public void setUp() {
        // Prerequisites
        User user = User.registerClient("testuser@velocity.com", "hashed_pw", "Test", "+48000500000", City.WARSAW);
        entityManager.persist(user);

        BikeModel model = BikeModel.create(
                "Urban Cruiser",
                "Standard commuter e-bike",
                25,
                60,
                120,
                BikeCategory.AGILITY
        );
        entityManager.persist(model);

        BikeInstance instance = BikeInstance.initialize(
                model,
                City.WARSAW
        );
        entityManager.persist(instance);

        // Target Data (Reservations)
        // res1: Month 9 (September 2026) -> Expected to be counted
        Reservation res1 = Reservation.book(
                user,
                instance,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 6),
                new BigDecimal("125.00")
        );
        res1.transitionTo(ReservationStatus.CONFIRMED, LocalDate.now(clock));
        entityManager.persist(res1);


        // res2: Month 9 (September 2026) -> Expected to be summed with res1 (125+125=250)
        Reservation res2 = Reservation.book(
                user,
                instance,
                LocalDate.of(2026, 9, 15),
                LocalDate.of(2026, 9, 20),
                new BigDecimal("125.00")
        );
        res2.transitionTo(ReservationStatus.CONFIRMED, LocalDate.now(clock));
        entityManager.persist(res2);

        // res3: Month 10 (October 2026) -> Expected to be ignored entirely
        Reservation res3 = Reservation.book(
                user,
                instance,
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 6),
                new BigDecimal("125.00")
        );
        res3.transitionTo(ReservationStatus.CANCELLED, LocalDate.now(clock));
        entityManager.persist(res3);

        entityManager.flush(); // Fire Inserts to PostgreSQL
        entityManager.clear(); // Wipe the cache to guarantee real DB queries
    }

    @Test
    public void findRevenueTrend_WithValidData_ReturnsGroupedResults() {
        List<RevenueByMonthProjection> result = reservationRepository.findRevenueTrend();

        assertThat(result).hasSize(1);
        RevenueByMonthProjection firstMonth = result.getFirst();
        assertThat(firstMonth.getRevenue()).isEqualByComparingTo(BigDecimal.valueOf(250));
    }

    @Test
    public void findTotalRevenue_WithMixedStatuses_SumsOnlyNonCancelled() {
        BigDecimal result = reservationRepository.findTotalRevenue();
        assertThat(result).isEqualByComparingTo(BigDecimal.valueOf(250));
    }

    @Test
    public void findTotalRevenue_WhenTableIsEmpty_ReturnsZero() {
        entityManager.getEntityManager().createQuery("DELETE FROM Reservation").executeUpdate();
        entityManager.clear();

        BigDecimal result = reservationRepository.findTotalRevenue();

        assertThat(result).isZero();
    }

    @Test
    public void countActiveRentals_WithConfirmedReservations_ReturnsCorrectCount() {
        long result = reservationRepository.countActiveRentals();
        assertThat(result).isEqualTo(2L);
    }

    @Test
    public void findFleetPopularity_WithValidData_ReturnsMappedProjections() {
        List<FleetPopularityProjection> result = reservationRepository.findFleetPopularity();

        assertThat(result).hasSize(1);
        FleetPopularityProjection popularity = result.getFirst();
        assertThat(popularity.getModelName()).isEqualTo("Urban Cruiser");
        assertThat(popularity.getCount()).isEqualTo(2L);
    }
}