package com.velocity.api.reservation.repository;

import com.velocity.api.reservation.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {
    @Query("""
              SELECT NOT EXISTS(
                SELECT 1 FROM Reservation r
                  WHERE r.bikeInstance.id = :bikeId
                    AND r.status IN ('PENDING', 'CONFIRMED')
                    AND r.startDate < :reqEndDate
                    AND r.endDate > :reqStartDate
              )
    """)
    public boolean isBikeAvailable(@Param("bikeId") UUID bikeId, @Param("reqStartDate") LocalDate reqStartDate, @Param("reqEndDate") LocalDate reqEndDate);
}
