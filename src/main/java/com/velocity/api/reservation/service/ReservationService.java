package com.velocity.api.reservation.service;

import com.velocity.api.common.exception.ResourceNotFoundException;
import com.velocity.api.reservation.Reservation;
import com.velocity.api.reservation.ReservationStatus;
import com.velocity.api.reservation.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReservationService {
    private final ReservationRepository reservationRepository;

    @Transactional
    public void transition(UUID reservationId, ReservationStatus newStatus) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found: " + reservationId));
        reservation.transitionTo(newStatus);
        reservationRepository.save(reservation);
    }
}
