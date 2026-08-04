package com.velocity.api.reservation.service;

import com.velocity.api.bike.BikeInstance;
import com.velocity.api.bike.BikeStatus;
import com.velocity.api.bike.repository.BikeInstanceRepository;
import com.velocity.api.billing.RentalCostCalculator;
import com.velocity.api.common.exception.BikeNotAvailableException;
import com.velocity.api.common.exception.InvalidBikeStateException;
import com.velocity.api.common.exception.ResourceNotFoundException;
import com.velocity.api.reservation.Reservation;
import com.velocity.api.reservation.ReservationStatus;
import com.velocity.api.reservation.dto.ReservationCreateRequest;
import com.velocity.api.reservation.dto.ReservationResponse;
import com.velocity.api.reservation.repository.ReservationRepository;
import com.velocity.api.user.User;
import com.velocity.api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReservationService {
    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final BikeInstanceRepository bikeInstanceRepository;
    private final RentalCostCalculator rentalCostCalculator;

    @Transactional
    public void transition(UUID reservationId, ReservationStatus newStatus) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found: " + reservationId));
        reservation.transitionTo(newStatus);
    }

    @Transactional
    public ReservationResponse createReservation(UUID userId, ReservationCreateRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        BikeInstance bike = bikeInstanceRepository.findById(req.bikeInstanceId())
                .orElseThrow(() -> new ResourceNotFoundException("Bike not found"));

        if (bike.getStatus() != BikeStatus.ACTIVE) {
            throw new InvalidBikeStateException("The bike does not have ACTIVE status.");
        }
        boolean isBikeAvailable = reservationRepository.isBikeAvailable(bike.getId(), req.startDate(), req.endDate());
        if (!isBikeAvailable) {
            throw new BikeNotAvailableException("The bike is not available for given date.");
        }

        int days = Math.toIntExact(ChronoUnit.DAYS.between(req.startDate(), req.endDate()));
        BigDecimal totalCost = rentalCostCalculator.calculate(days);

        Reservation reservation = new Reservation(user, bike, req.startDate(), req.endDate(), totalCost);
        Reservation savedReservation = reservationRepository.save(reservation);
        ReservationResponse.BikeSummary bikeSummary = new ReservationResponse.BikeSummary(bike.getId(), bike.getBikeModel().getName(), bike.getCity());
        return new ReservationResponse(
                savedReservation.getId(),
                savedReservation.getStartDate(),
                savedReservation.getEndDate(),
                savedReservation.getTotalCost(),
                savedReservation.getStatus(),
                savedReservation.getCreatedAt(),
                bikeSummary
        );
    }
}
