package com.velocity.api.user.service;


import com.velocity.api.bike.BikeInstance;
import com.velocity.api.bike.BikeStatus;
import com.velocity.api.bike.repository.BikeInstanceRepository;
import com.velocity.api.bike.repository.projection.InstanceProjection;
import com.velocity.api.common.City;
import com.velocity.api.common.exception.ResourceNotFoundException;
import com.velocity.api.security.JwtService;
import com.velocity.api.security.repository.TokenBlacklistRepository;
import com.velocity.api.user.User;
import com.velocity.api.user.UserRole;
import com.velocity.api.user.dto.AdminUserResponse;
import com.velocity.api.user.dto.AdminUserUpdateRequest;
import com.velocity.api.user.dto.BikeInstanceResponse;
import com.velocity.api.user.exception.CannotDemoteSelfException;
import com.velocity.api.user.exception.EmailAlreadyRegisteredException;
import com.velocity.api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Service
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminUserService {
    private final UserRepository userRepository;
    private final TokenBlacklistRepository tokenBlacklistRepository;
    private final JwtService jwtService;
    private final BikeInstanceRepository bikeInstanceRepository;

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
        Duration tokenExpirationDuration = jwtService.getJwtExpirationDuration();
        tokenBlacklistRepository.blacklistUser(String.valueOf(id), tokenExpirationDuration);
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

        if (!email.equalsIgnoreCase(user.getEmail()) && userRepository.findByEmail(email).isPresent()) {
            throw new EmailAlreadyRegisteredException("This email address is already in use.");
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
    public void updateBikeStatus(UUID id, BikeStatus status, Long version) {
        BikeInstance bikeInstance = bikeInstanceRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Bike not found."));
        if (!bikeInstance.getVersion().equals(version)) {
            throw new ObjectOptimisticLockingFailureException(BikeInstance.class, id);
        }
        bikeInstance.transitionTo(status);
    }
}
