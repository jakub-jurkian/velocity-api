package com.velocity.api.user.service;

import com.velocity.api.common.exception.EmailAlreadyRegisteredException;
import com.velocity.api.user.User;
import com.velocity.api.user.dto.UserRegistrationRequest;
import com.velocity.api.user.dto.UserRegistrationResponse;
import com.velocity.api.user.repository.UserRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

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
}
