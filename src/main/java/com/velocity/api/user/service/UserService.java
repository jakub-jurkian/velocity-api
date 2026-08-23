package com.velocity.api.user.service;

import com.velocity.api.common.exception.EmailAlreadyRegisteredException;
import com.velocity.api.common.exception.ResourceNotFoundException;
import com.velocity.api.user.User;
import com.velocity.api.user.dto.UserProfileResponse;
import com.velocity.api.user.dto.UserRegistrationRequest;
import com.velocity.api.user.dto.UserRegistrationResponse;
import com.velocity.api.user.repository.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserRegistrationResponse registerUser(UserRegistrationRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyRegisteredException("The email address " + request.email() + " is already in use.");
        }
        String encodedPassword = passwordEncoder.encode(request.password());
        User user = User.registerClient(request.email(), encodedPassword, request.fullName(), request.phone(), request.city());

        User registeredUser = userRepository.save(user);
        log.info("Successfully registered new user with ID: {} and email: {}", registeredUser.getId(), registeredUser.getEmail());
        return new UserRegistrationResponse(
                registeredUser.getId(),
                registeredUser.getEmail(),
                registeredUser.getFullName(),
                registeredUser.getPhone(),
                registeredUser.getCity(),
                registeredUser.getRole()
        );
    }

    public UserProfileResponse getProfile() {
        UserDetails userDetails = (UserDetails) Objects.requireNonNull(SecurityContextHolder.getContext().getAuthentication()).getPrincipal();
        assert userDetails != null;
        String email = userDetails.getUsername();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
        return new UserProfileResponse(user.getId(), user.getEmail(), user.getFullName(), user.getPhone(), user.getRole(), user.getCity(), user.getJoinedDate());
    }
}
