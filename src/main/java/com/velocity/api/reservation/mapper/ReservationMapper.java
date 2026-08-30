package com.velocity.api.reservation.mapper;

import com.velocity.api.reservation.Reservation;
import com.velocity.api.reservation.dto.BikeSummary;
import com.velocity.api.reservation.dto.ReservationResponse;
import org.springframework.stereotype.Component;

@Component
public class ReservationMapper {
    public ReservationResponse toDto(Reservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getStartDate(),
                reservation.getEndDate(),
                reservation.getTotalCost(),
                reservation.getStatus(),
                new BikeSummary(reservation.getBikeInstance().getId(),
                        reservation.getBikeInstance().getBikeModel().getName(),
                        reservation.getBikeInstance().getCity()
                )
        );
    }
}
