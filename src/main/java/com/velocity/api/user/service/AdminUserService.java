package com.velocity.api.user.service;


import com.velocity.api.bike.BikeInstance;
import com.velocity.api.bike.BikeStatus;
import com.velocity.api.bike.exception.BikeUnderActiveRentalException;
import com.velocity.api.bike.repository.BikeInstanceRepository;
import com.velocity.api.bike.repository.projection.InstanceProjection;
import com.velocity.api.common.City;
import com.velocity.api.common.exception.ResourceNotFoundException;
import com.velocity.api.reservation.Reservation;
import com.velocity.api.reservation.dto.ConflictDto;
import com.velocity.api.reservation.repository.ReservationRepository;
import com.velocity.api.user.User;
import com.velocity.api.user.UserRole;
import com.velocity.api.user.dto.AdminUserResponse;
import com.velocity.api.user.dto.AdminUserUpdateRequest;
import com.velocity.api.user.dto.BikeInstanceResponse;
import com.velocity.api.user.exception.CannotDemoteSelfException;
import com.velocity.api.user.exception.EmailAlreadyRegisteredException;
import com.velocity.api.user.exception.PhoneAlreadyRegisteredException;
import com.velocity.api.user.repository.UserRepository;
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
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminUserService {
    private final UserRepository userRepository;
    private final BikeInstanceRepository bikeInstanceRepository;
    private final ReservationRepository reservationRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Page<AdminUserResponse> listUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(AdminUserResponse::from);
    }

    @Transactional
    public void blockUser(UUID id, UUID authenticatedAdminId) {
        if (authenticatedAdminId.equals(id)) {
            throw new CannotDemoteSelfException("Administrators cannot block their own accounts.");
        }
        User user = userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found."));
        user.block();
    }

    @Transactional
    public void unblockUser(UUID id) {
        User user = userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found."));
        user.unblock();
    }

    @Transactional
    public void updateUser(UUID id, AdminUserUpdateRequest request) {
        User user = userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found."));
        String fullName = request.fullName().isPresent() ? request.fullName().get() : user.getFullName();
        String phone = request.phone().isPresent() ? request.phone().get() : user.getPhone();
        City city = request.city().isPresent() ? request.city().get() : user.getCity();
        String email = request.email().isPresent() ? request.email().get() : user.getEmail();

        if (userRepository.isEmailTakenByAnotherUser(email, id)) {
            throw new EmailAlreadyRegisteredException("An account with this email already exists.");
        }

        if (userRepository.isPhoneTakenByAnotherUser(phone, id)) {
            throw new PhoneAlreadyRegisteredException("An account with this phone number already exists.");
        }

        user.updateProfileByAdmin(fullName, phone, city, email);
    }

    @Transactional
    public void updateUserRole(UUID id, UUID authenticatedUserId, UserRole role) {
        if (authenticatedUserId.equals(id)) {
            throw new CannotDemoteSelfException("Administrators cannot change their own roles.");
        }
        User user = userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found."));
        user.changeRoleByAdmin(role);
    }

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
}
