package com.velocity.api.reservation.service;

import com.velocity.api.bike.BikeInstance;
import com.velocity.api.bike.BikeStatus;
import com.velocity.api.bike.repository.BikeInstanceRepository;
import com.velocity.api.bike.repository.projection.AvailableModelProjection;
import com.velocity.api.billing.RentalCostCalculator;
import com.velocity.api.common.exception.BikeNotAvailableException;
import com.velocity.api.common.exception.InvalidBikeStateException;
import com.velocity.api.common.exception.ResourceNotFoundException;
import com.velocity.api.reservation.Reservation;
import com.velocity.api.reservation.ReservationStatus;
import com.velocity.api.reservation.dto.AvailableModelResponse;
import com.velocity.api.reservation.dto.ReservationBookRequest;
import com.velocity.api.reservation.dto.ReservationBookResponse;
import com.velocity.api.reservation.repository.ReservationRepository;
import com.velocity.api.user.User;
import com.velocity.api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationService {
    private final BikeInstanceRepository bikeInstanceRepository;
    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final RentalCostCalculator rentalCostCalculator;

    @Transactional
    public void transitionStatus(UUID reservationId, ReservationStatus newStatus) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found: " + reservationId));
        reservation.transitionTo(newStatus);
        log.info("Reservation {} transitioned to {}", reservationId, newStatus);
    }

    @Transactional
    public ReservationBookResponse book(UUID userId, ReservationBookRequest req) {
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

        Reservation reservation = Reservation.book(user, bike, req.startDate(), req.endDate(), totalCost);
        Reservation bookedReservation = reservationRepository.save(reservation);
        log.info(
                "Booked reservation {} for user {} on bike {} from {} to {}",
                bookedReservation.getId(),
                userId,
                bike.getId(),
                req.startDate(),
                req.endDate()
        );
        ReservationBookResponse.BikeSummary bikeSummary = new ReservationBookResponse.BikeSummary(bike.getId(), bike.getBikeModel().getName(), bike.getCity());
        return new ReservationBookResponse(
                bookedReservation.getId(),
                bookedReservation.getStartDate(),
                bookedReservation.getEndDate(),
                bookedReservation.getTotalCost(),
                bookedReservation.getStatus(),
                bookedReservation.getCreatedAt(),
                bikeSummary
        );
    }

    public List<AvailableModelResponse> getAvailableModels(LocalDate startDate, LocalDate endDate) {
        List<AvailableModelProjection> availableProjections = bikeInstanceRepository.findAvailableModels(startDate, endDate);
        int days = Math.toIntExact(ChronoUnit.DAYS.between(startDate, endDate));
        BigDecimal totalCost = rentalCostCalculator.calculate(days);
        return availableProjections.stream().map(projection ->
                        new AvailableModelResponse(
                                projection.getBookableInstanceId(),
                                projection.getModelName(),
                                projection.getModelDescription(),
                                projection.getModelSpeed(),
                                projection.getModelRange(),
                                projection.getModelCapacity(),
                                projection.getModelCategory(),
                                totalCost
                        )
                )
                .toList();
    }
}
