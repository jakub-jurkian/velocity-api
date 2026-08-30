package com.velocity.api.user.controller;

import com.velocity.api.user.dto.UserProfileUpdateRequest;
import com.velocity.api.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @PreAuthorize("#id == authentication.principal.id")
    @PatchMapping("/{id}")
    public ResponseEntity<Void> updateProfile(@PathVariable("id") UUID id, @RequestBody UserProfileUpdateRequest request) {
        userService.updateProfile(id, request);
        return ResponseEntity.noContent().build();
    }
}
