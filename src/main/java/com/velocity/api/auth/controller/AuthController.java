package com.velocity.api.auth.controller;

import com.velocity.api.auth.dto.UserLoginRequest;
import com.velocity.api.auth.dto.UserLoginResponse;
import com.velocity.api.auth.dto.UserRegistrationRequest;
import com.velocity.api.auth.dto.UserRegistrationResponse;
import com.velocity.api.auth.service.AuthService;
import com.velocity.api.user.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<UserRegistrationResponse> registerUser(@Valid @RequestBody UserRegistrationRequest request) {
        UserRegistrationResponse response = authService.registerUser(request);

        URI location = ServletUriComponentsBuilder
                .fromCurrentContextPath()       // Gets http://localhost:8080
                .path("/api/v1/users/{id}")     // The path to the future user profile
                .buildAndExpand(response.id()) // Replaces {id} with the real UUID
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<UserLoginResponse> loginUser(@Valid @RequestBody UserLoginRequest request) {
        UserLoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logoutUser(@RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        if ((authHeader.isBlank() || !authHeader.startsWith("Bearer ")) || authHeader.length() > 7) {
            throw new BadCredentialsException("Invalid or missing Bearer token");
        }
        String token = authHeader.substring(7).strip();
        authService.logout(token);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getProfile() {
        UserProfileResponse response = authService.getProfile();
        return ResponseEntity.ok(response);
    }
}
