package com.velocity.api.user.service;


import com.velocity.api.common.City;
import com.velocity.api.common.exception.ResourceNotFoundException;
import com.velocity.api.user.User;
import com.velocity.api.user.UserRole;
import com.velocity.api.user.dto.AdminUserResponse;
import com.velocity.api.user.dto.AdminUserUpdateRequest;
import com.velocity.api.user.exception.CannotDemoteSelfException;
import com.velocity.api.user.exception.EmailAlreadyRegisteredException;
import com.velocity.api.user.exception.PhoneAlreadyRegisteredException;
import com.velocity.api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminUserService {
    private final UserRepository userRepository;

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
}
