package com.velocity.api.user.service;

import com.velocity.api.security.JwtService;
import com.velocity.api.security.repository.TokenBlacklistRepository;
import com.velocity.api.user.User;
import com.velocity.api.user.dto.UserLoginRequest;
import com.velocity.api.user.dto.UserLoginResponse;
import com.velocity.api.user.dto.UserLogoutRequest;
import com.velocity.api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Date;
import java.util.HashMap;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final TokenBlacklistRepository tokenBlacklistRepository;

    @Value("${security.jwt.expiration-ms}")
    private long jwtExpiration;

    public UserLoginResponse login(UserLoginRequest request) {
        // password check
        Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        // gather extra claims
        User user = userRepository.findByEmail(request.email());
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
        Date expirationDate = jwtService.extractExpration(token);
        Duration ttl = Duration.between(Date, expirationDate);
        if (!ttl.isNegative() && !ttl.isZero()) tokenBlacklistRepository.blacklist(token, ttl);
    }
}
