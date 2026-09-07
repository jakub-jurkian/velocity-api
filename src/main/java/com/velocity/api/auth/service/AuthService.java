package com.velocity.api.auth.service;

import com.velocity.api.auth.dto.UserRegistrationRequest;
import com.velocity.api.auth.dto.UserRegistrationResponse;
import com.velocity.api.common.exception.ResourceNotFoundException;
import com.velocity.api.security.CustomUserDetails;
import com.velocity.api.security.JwtService;
import com.velocity.api.security.repository.TokenBlacklistRepository;
import com.velocity.api.user.User;
import com.velocity.api.auth.dto.UserLoginRequest;
import com.velocity.api.auth.dto.UserLoginResponse;
import com.velocity.api.auth.dto.UserProfileResponse;
import com.velocity.api.user.exception.EmailAlreadyRegisteredException;
import com.velocity.api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {
    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final TokenBlacklistRepository tokenBlacklistRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    @Value("${security.jwt.expiration-ms}")
    private long jwtExpiration;

    @Transactional
    public UserRegistrationResponse register(UserRegistrationRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyRegisteredException("The email address " + request.email() + " is already in use.");
        }

        String encodedPassword = passwordEncoder.encode(request.password());
        User user = User.registerClient(request.email(), encodedPassword, request.fullName(), request.phone(), request.city());

        User registeredUser = userRepository.save(user);
        log.info("Successfully registered new user with ID: {} and email: {}", registeredUser.getId(), registeredUser.getEmail());
        return UserRegistrationResponse.from(registeredUser);
    }

    public UserLoginResponse login(UserLoginRequest request) {
        // password check
        Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        // Cast the principal (the logged-in entity) to Spring UserDetails object
        if (!(authentication.getPrincipal() instanceof CustomUserDetails principal)) {
            throw new IllegalStateException("Authentication principal is not CustomUserDetails");
        }

        // gather extra claims
        HashMap<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("id", principal.getId());
        extraClaims.put("role", principal.getRole());

        // call token generator
        String generatedToken = jwtService.generateToken(extraClaims, principal);
        return new UserLoginResponse(generatedToken, "Bearer", jwtExpiration);
    }

    public void logout(String token) {
        Instant expirationDate = jwtService.extractExpiration(token);
        Duration ttl = Duration.between(clock.instant(), expirationDate);
        if (!ttl.isNegative() && !ttl.isZero()) tokenBlacklistRepository.blacklist(token, ttl);
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
        return UserProfileResponse.from(user);
    }
}
