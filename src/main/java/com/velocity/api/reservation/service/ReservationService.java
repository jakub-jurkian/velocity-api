package com.velocity.api.reservation.service;

import com.velocity.api.bike.BikeInstance;
import com.velocity.api.bike.BikeStatus;
import com.velocity.api.bike.exception.BikeUnderActiveRentalException;
import com.velocity.api.bike.repository.BikeInstanceRepository;
import com.velocity.api.bike.repository.projection.AvailableModelProjection;
import com.velocity.api.common.City;
import com.velocity.api.pricing.RentalCostCalculator;
import com.velocity.api.bike.exception.BikeNotAvailableException;
import com.velocity.api.bike.exception.InvalidBikeStateException;
import com.velocity.api.common.exception.ResourceNotFoundException;
import com.velocity.api.pricing.dto.RentalQuote;
import com.velocity.api.reservation.Reservation;
import com.velocity.api.reservation.ReservationStatus;
import com.velocity.api.reservation.dto.*;
import com.velocity.api.reservation.exception.LateCancelException;
import com.velocity.api.reservation.mapper.ReservationMapper;
import com.velocity.api.reservation.repository.ReservationRepository;
import com.velocity.api.user.User;
import com.velocity.api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReservationService {
    private final BikeInstanceRepository bikeInstanceRepository;
    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final RentalCostCalculator rentalCostCalculator;
    private final ReservationMapper reservationMapper;
    private final Clock clock;

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
        BigDecimal totalCost = rentalCostCalculator.calculateQuote(days).totalCost();
        LocalDate currentDate = LocalDate.now(clock);
        Reservation reservation = Reservation.book(user, bike, req.startDate(), req.endDate(), currentDate, totalCost);
        Reservation bookedReservation = reservationRepository.save(reservation);
        log.info(
                "Booked reservation {} for user {} on bike {} from {} to {}",
                bookedReservation.getId(),
                userId,
                bike.getId(),
                req.startDate(),
                req.endDate()
        );
        BikeSummary bikeSummary = BikeSummary.from(bike);
        return ReservationBookResponse.from(bookedReservation, bikeSummary);
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse getAvailableModels(LocalDate startDate, LocalDate endDate, City userCity) {
        List<AvailableModelProjection> availableProjections = bikeInstanceRepository.findAvailableModels(startDate, endDate, userCity.toString());
        int days = Math.toIntExact(ChronoUnit.DAYS.between(startDate, endDate));
        RentalQuote rentalQuote = rentalCostCalculator.calculateQuote(days);
        List<AvailableBikeModel> availableBikeModel = availableProjections.stream().map(AvailableBikeModel::from).toList();
        return new AvailabilityResponse(rentalQuote, availableBikeModel);
    }

    private void transitionStatus(UUID reservationId, ReservationStatus newStatus) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found: " + reservationId));
        reservation.transitionTo(newStatus, LocalDate.now(clock));
        log.info("Reservation {} transitioned to {}", reservationId, newStatus);
    }

    @Transactional // if not added, status will be updated in Java memory only.
    public void cancelStaleReservation(UUID id) {
        transitionStatus(id, ReservationStatus.CANCELLED);
    }

    @Transactional
    public void completePastDueConfirmedReservations(UUID id) {
        transitionStatus(id, ReservationStatus.COMPLETED);
    }

    @Transactional(readOnly = true)
    public Page<ReservationResponse> getUserReservations(UUID id, Pageable page) {
        Page<Reservation> reservationsPage = reservationRepository.findByUserId(id, page);
        return reservationsPage.map(reservationMapper::toDto);
    }

    @Transactional
    public void confirmReservation(UUID reservationId, UUID userId) {
        Reservation reservation = reservationRepository.findByIdAndUserId(reservationId, userId).orElseThrow(() -> new ResourceNotFoundException("Reservation not found"));

        reservation.transitionTo(ReservationStatus.CONFIRMED, LocalDate.now(clock));
    }

    @Transactional
    public void cancelReservation(UUID reservationId, UUID userId) throws LateCancelException {
        Reservation reservation = reservationRepository.findByIdAndUserId(reservationId, userId).orElseThrow(() -> new ResourceNotFoundException("Reservation not found"));

        reservation.transitionTo(ReservationStatus.CANCELLED, LocalDate.now(clock));
    }
}
