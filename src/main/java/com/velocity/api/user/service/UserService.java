package com.velocity.api.user.service;

import com.velocity.api.common.City;
import com.velocity.api.common.exception.ResourceNotFoundException;
import com.velocity.api.user.User;
import com.velocity.api.auth.dto.UserProfileUpdateRequest;
import com.velocity.api.user.repository.UserRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {
    private final UserRepository userRepository;

    @Transactional
    public void updateProfile(UUID userId, UserProfileUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));

        String fullName = request.fullName().isPresent() ? request.fullName().get() : user.getFullName();
        String phone = request.phone().isPresent() ? request.phone().get() : user.getPhone();
        City city = request.city().isPresent() ? request.city().get() : user.getCity();

        user.updateProfile(fullName, phone, city);
    }
}
