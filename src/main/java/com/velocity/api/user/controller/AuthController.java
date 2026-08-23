package com.velocity.api.user.controller;

import com.velocity.api.user.dto.UserLoginRequest;
import com.velocity.api.user.dto.UserLoginResponse;
import com.velocity.api.user.dto.UserRegistrationRequest;
import com.velocity.api.user.dto.UserRegistrationResponse;
import com.velocity.api.user.service.AuthService;
import com.velocity.api.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final UserService userService;
    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<UserRegistrationResponse> registerUser(@Valid @RequestBody UserRegistrationRequest request) {
        UserRegistrationResponse response = userService.registerUser(request);

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
        if (authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            authService.logout(token);
        }
        return ResponseEntity.noContent().build();
    }
}
