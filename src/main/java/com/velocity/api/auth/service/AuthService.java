package com.velocity.api.auth.service;

import com.velocity.api.auth.dto.UserRegistrationRequest;
import com.velocity.api.auth.dto.UserRegistrationResponse;
import com.velocity.api.common.exception.ResourceNotFoundException;
import com.velocity.api.security.JwtService;
import com.velocity.api.security.repository.TokenBlacklistRepository;
import com.velocity.api.user.User;
import com.velocity.api.auth.dto.UserLoginRequest;
import com.velocity.api.auth.dto.UserLoginResponse;
import com.velocity.api.user.dto.UserProfileResponse;
import com.velocity.api.user.exception.EmailAlreadyRegisteredException;
import com.velocity.api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Objects;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {
    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final TokenBlacklistRepository tokenBlacklistRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${security.jwt.expiration-ms}")
    private long jwtExpiration;

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

    public UserLoginResponse login(UserLoginRequest request) {
        // password check
        Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        // gather extra claims
        User user = userRepository.findByEmail(request.email()).orElseThrow(() -> new BadCredentialsException("Bad credentials"));
        HashMap<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("id", user.getId());
        extraClaims.put("role", user.getRole());

        // UserDetails userDetails = customUserDetailsService.loadUserByUsername(request.email());
        // Cast the principal (the logged-in entity) to our Spring UserDetails object
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();

        // call token generator
        assert userDetails != null;
        String generatedToken = jwtService.generateToken(extraClaims, userDetails);
        return new UserLoginResponse(generatedToken, "Bearer", jwtExpiration);
    }

    public void logout(String token) {
        Instant expirationDate = jwtService.extractExpiration(token);
        Duration ttl = Duration.between(Instant.now(), expirationDate);
        if (!ttl.isNegative() && !ttl.isZero()) tokenBlacklistRepository.blacklist(token, ttl);
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
