package com.velocity.api.user.controller;

import com.velocity.api.user.dto.UserRegistrationDto;
import com.velocity.api.user.dto.UserRegistrationResponse;
import com.velocity.api.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<UserRegistrationResponse> registerUser(@Valid @RequestBody UserRegistrationDto requestDto) {
        UserRegistrationResponse createdUser = userService.registerUser(requestDto);

        URI location = ServletUriComponentsBuilder
                .fromCurrentContextPath()       // Gets http://localhost:8080
                .path("/api/v1/users/{id}")     // The path to the future user profile
                .buildAndExpand(createdUser.id()) // Replaces {id} with the real UUID
                .toUri();

        return ResponseEntity.created(location).body(createdUser);
    }
}
