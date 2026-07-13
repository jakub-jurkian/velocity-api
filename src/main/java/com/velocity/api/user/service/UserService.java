package com.velocity.api.user.service;

import com.velocity.api.user.User;
import com.velocity.api.user.UserRole;
import com.velocity.api.user.UserStatus;
import com.velocity.api.user.dto.UserRegistrationDto;
import com.velocity.api.user.dto.UserRegistrationResponse;
import com.velocity.api.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    /**
     * Registers a new client in the system.
     *
     * @param dto the validated registration payload
     * @return UserRegistrationResponse safely omitting the password hash
     * @throws IllegalStateException if the email is already in use
     */
    @Transactional // tells Spring this entire method is a single db transaction.
    public UserRegistrationResponse registerUser(UserRegistrationDto dto) {
        if (userRepository.existsByEmail(dto.email())) {
            throw new IllegalStateException("Email is already registered");
        }
        ;
        User user = new User();
        user.setEmail(dto.email());
        user.setFullName(dto.fullName());
        user.setPhone(dto.phone());
        user.setCity(dto.city());
        user.setPasswordHash(passwordEncoder.encode(dto.password()));

        user.setRole(UserRole.CLIENT);
        user.setStatus(UserStatus.ACTIVE);

        User savedUser = userRepository.save(user);

        return new UserRegistrationResponse(
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getFullName(),
                savedUser.getPhone(),
                savedUser.getCity(),
                savedUser.getRole()
        );
    }
}
