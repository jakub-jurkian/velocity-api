package com.velocity.api.reservation.service;

import com.velocity.api.bike.BikeInstance;
import com.velocity.api.bike.repository.BikeInstanceRepository;
import com.velocity.api.bike.repository.projection.AvailableModelProjection;
import com.velocity.api.common.City;
import com.velocity.api.pricing.RentalCostCalculator;
import com.velocity.api.bike.exception.BikeNotAvailableException;
import com.velocity.api.common.exception.ResourceNotFoundException;
import com.velocity.api.pricing.dto.RentalQuote;
import com.velocity.api.reservation.RentalPeriod;
import com.velocity.api.reservation.Reservation;
import com.velocity.api.reservation.ReservationStatus;
import com.velocity.api.reservation.dto.*;
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
import java.time.Instant;
import java.time.LocalDate;
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
        bike.assertBookableIn(user.getCity());

        RentalPeriod period = req.period();
        if (!reservationRepository.isBikeAvailable(bike.getId(), period.startDate(), period.endDate())) {
            throw new BikeNotAvailableException("The bike is not available for given date.");
        }

        BigDecimal totalCost = rentalCostCalculator.calculateQuote(period.days()).totalCost();
        Reservation reservation = Reservation.book(user, bike, period, LocalDate.now(clock), totalCost);
        Reservation bookedReservation = reservationRepository.save(reservation);
        log.info(
                "Booked reservation {} for user {} on bike {} from {} to {}",
                bookedReservation.getId(),
                userId,
                bike.getId(),
                period.startDate(),
                period.endDate()
        );
        BikeSummary bikeSummary = BikeSummary.from(bike);
        return ReservationBookResponse.from(bookedReservation, bikeSummary);
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse getAvailableModels(RentalPeriod period, City userCity) {
        List<AvailableModelProjection> availableProjections = bikeInstanceRepository.findAvailableModels(period.startDate(), period.endDate(), userCity.toString());
        RentalQuote rentalQuote = rentalCostCalculator.calculateQuote(period.days());
        List<AvailableBikeModel> availableBikeModel = availableProjections.stream().map(AvailableBikeModel::from).toList();
        return new AvailabilityResponse(rentalQuote, availableBikeModel);
    }

    @Transactional
    public void expireStaleReservation(UUID id) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found: " + id));
        reservation.expire(Instant.now(clock));
        log.info("Reservation {} is now {}", id, reservation.getStatus());
    }

    @Transactional
    public void completePastDueConfirmedReservations(UUID id) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found: " + id));
        reservation.transitionTo(ReservationStatus.COMPLETED, LocalDate.now(clock));
        log.info("Reservation {} transitioned to {}", id, ReservationStatus.COMPLETED);
    }

    @Transactional(readOnly = true)
    public Page<ReservationResponse> getUserReservations(UUID id, Pageable page) {
        Page<Reservation> reservationsPage = reservationRepository.findByUserId(id, page);
        return reservationsPage.map(reservationMapper::toDto);
    }

    @Transactional(readOnly = true)
    public ReservationResponse getUserReservation(UUID reservationId, UUID userId) {
        Reservation reservation = reservationRepository.findByIdAndUserId(reservationId, userId).orElseThrow(() -> new ResourceNotFoundException("Reservation not found"));
        return reservationMapper.toDto(reservation);
    }

    @Transactional
    public void confirmReservation(UUID reservationId, UUID userId) {
        Reservation reservation = reservationRepository.findByIdAndUserId(reservationId, userId).orElseThrow(() -> new ResourceNotFoundException("Reservation not found"));

        reservation.confirm(Instant.now(clock));
    }

    @Transactional
    public void cancelReservation(UUID reservationId, UUID userId) {
        Reservation reservation = reservationRepository.findByIdAndUserId(reservationId, userId).orElseThrow(() -> new ResourceNotFoundException("Reservation not found"));

        reservation.transitionTo(ReservationStatus.CANCELLED, LocalDate.now(clock));
    }
}
