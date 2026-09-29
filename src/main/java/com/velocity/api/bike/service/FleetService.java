package com.velocity.api.bike.service;

import com.velocity.api.bike.BikeInstance;
import com.velocity.api.bike.BikeStatus;
import com.velocity.api.bike.dto.BikeInstanceCountResponse;
import com.velocity.api.bike.exception.BikeUnderActiveRentalException;
import com.velocity.api.bike.repository.BikeInstanceRepository;
import com.velocity.api.bike.repository.projection.InstanceProjection;
import com.velocity.api.common.City;
import com.velocity.api.common.exception.ResourceNotFoundException;
import com.velocity.api.reservation.Reservation;
import com.velocity.api.reservation.dto.ConflictDto;
import com.velocity.api.reservation.repository.ReservationRepository;
import com.velocity.api.bike.dto.BikeInstanceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FleetService {
    private final BikeInstanceRepository bikeInstanceRepository;
    private final ReservationRepository reservationRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public BikeInstanceCountResponse getBikesCount(City city, BikeStatus status) {
        long count = bikeInstanceRepository.countByStatusAndCity(status, city);
        return new BikeInstanceCountResponse(count);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void updateBikeStatus(UUID bikeId, BikeStatus status, Long version, boolean force) {
        LocalDate currentDate = LocalDate.now(clock);
        BikeInstance bikeInstance = bikeInstanceRepository.findById(bikeId).orElseThrow(() -> new ResourceNotFoundException("Bike not found"));
        if (!bikeInstance.getVersion().equals(version)) {
            throw new OptimisticLockingFailureException("The bike was modified by another user. Please refresh.");
        }
        if (status != BikeStatus.ACTIVE) {
            List<Reservation> conflicts = reservationRepository.findActiveConflictsForBike(bikeId, currentDate);
            if (!conflicts.isEmpty()) {
                if (!force) {
                    List<ConflictDto> dtos = conflicts.stream().map(r -> new ConflictDto(r.getId(), r.getUser().getEmail(), r.getStartDate(), r.getEndDate())).toList();
                    throw new BikeUnderActiveRentalException(dtos);
                } else {
                    // cancelByOperator, not transitionTo: the conflict query
                    // deliberately includes rentals already under way, and the
                    // customer-facing late-cancel rule would refuse precisely
                    // those — a bike that is out with a rider and needs to come
                    // off the road is the reason this path exists.
                    String cancellationReason = "Cancelled by Admin: Bike transitioned to " + status;
                    conflicts.forEach(r -> r.cancelByOperator(cancellationReason));
                }
            }
        }
        bikeInstance.transitionTo(status);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public Page<BikeInstanceResponse> getBikes(Optional<BikeStatus> status, Pageable pageable) {
        Page<InstanceProjection> response;
        if (status.isEmpty()) {
            response = bikeInstanceRepository.findBy(pageable);
        } else {
            response = bikeInstanceRepository.findByStatus(status.get(), pageable);
        }
        return response.map(BikeInstanceResponse::from);
    }
}
