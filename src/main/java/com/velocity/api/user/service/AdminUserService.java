package com.velocity.api.user.service;

import com.velocity.api.common.exception.ResourceNotFoundException;
import com.velocity.api.user.User;
import com.velocity.api.user.dto.AdminUserResponse;
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

    public Page<AdminUserResponse> listUsers(Pageable pageable) {
        return userRepository.findAll(pageable)
                .map(u -> new AdminUserResponse(
                        u.getId(),
                        u.getEmail(),
                        u.getFullName(),
                        u.getPhone(),
                        u.getStatus(),
                        u.getRole(),
                        u.getJoinedDate()));
    }

    @Transactional
    public void blockUser(UUID id) {
        User user = userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found."));
        user.block();
    }

    @Transactional
    public void unblockUser(UUID id) {
        User user = userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found."));
        user.unblock();
    }

    @Transactional
    public void softDeleteUser(UUID id) {
        User user = userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found."));
        user.softDelete();
    }
}
