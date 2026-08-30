package com.velocity.api.reservation.repository;

import com.velocity.api.reservation.Reservation;
import com.velocity.api.reservation.ReservationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {
    @Query("""
                      SELECT NOT EXISTS(
                        SELECT 1 FROM Reservation r
                          WHERE r.bikeInstance.id = :bikeId
                            AND r.status IN ('PENDING', 'CONFIRMED')
                            AND r.startDate < :endDate
                            AND r.endDate > :startDate
                      )
            """)
    boolean isBikeAvailable(@Param("bikeId") UUID bikeId, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("SELECT r.id FROM Reservation r WHERE r.status = :status AND r.createdAt < :cutoff")
    List<UUID> findStalePendingReservationsIds(@Param("status") ReservationStatus status, @Param("cutoff") Instant cutoff);

    @Query("SELECT r.id FROM Reservation r WHERE r.status = :status AND r.endDate < :today")
    List<UUID> findPastDueConfirmedReservationsIds(@Param("status") ReservationStatus status, @Param("today") LocalDate today);

    @EntityGraph(attributePaths = {"bikeInstance", "bikeInstance.bikeModel", "bikeInstance.id", "bikeInstance.city"})
    Page<Reservation> findByUserId(UUID userId, Pageable pageable);
}
