package com.velocity.api.user.controller;

import com.velocity.api.auth.dto.UserProfileUpdateRequest;
import com.velocity.api.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "Endpoints for managing the authenticated user's account and profile data")
public class UserController {
    private final UserService userService;

    @PreAuthorize("#id == authentication.principal.id")
    @PatchMapping("/{id}")
    @Operation(
            summary = "Update user profile",
            description = "Partially updates account details for the specified user. A user can only update their own profile."
    )
    public ResponseEntity<Void> updateProfile(@PathVariable("id") UUID id, @Valid @RequestBody UserProfileUpdateRequest request) {
        userService.updateProfile(id, request);
        return ResponseEntity.noContent().build();
    }
}
