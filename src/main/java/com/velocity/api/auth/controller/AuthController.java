package com.velocity.api.auth.controller;

import com.velocity.api.auth.dto.*;
import com.velocity.api.auth.service.AuthService;
import com.velocity.api.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Identity, session management, and profile access")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/register")
    @Operation(
            summary = "Register a new user",
            description = "Creates a new user account with default USER privileges."
    )
    public ResponseEntity<UserRegistrationResponse> registerUser(@Valid @RequestBody UserRegistrationRequest request) {
        UserRegistrationResponse response = authService.register(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    @Operation(
            summary = "Authenticate user",
            description = "Validates credentials and returns a JWT Bearer token for session authorization."
    )
    public ResponseEntity<UserLoginResponse> loginUser(@Valid @RequestBody UserLoginRequest request) {
        UserLoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    @Operation(
            summary = "Invalidate session",
            description = "Adds the current JWT to the Redis blacklist, expiring the active session."
    )
    public ResponseEntity<Void> logoutUser(@RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        if ((authHeader.isBlank() || !authHeader.startsWith("Bearer ")) || authHeader.length() < 8) {
            throw new BadCredentialsException("Invalid or missing Bearer token");
        }
        String token = authHeader.substring(7).strip();
        authService.logout(token);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @Operation(
            summary = "Get current user profile",
            description = "Fetches the profile details of the currently authenticated user based on their JWT."
    )
    public ResponseEntity<UserProfileResponse> getProfile(@AuthenticationPrincipal CustomUserDetails customUserDetails) {
        String authenticatedUserEmail = customUserDetails.getUsername();
        UserProfileResponse response = authService.getProfile(authenticatedUserEmail);
        return ResponseEntity.ok(response);
    }
}
