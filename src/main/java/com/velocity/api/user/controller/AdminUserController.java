package com.velocity.api.user.controller;

import com.velocity.api.common.dto.PaginatedResponse;
import com.velocity.api.security.CustomUserDetails;
import com.velocity.api.user.dto.AdminUserResponse;
import com.velocity.api.user.dto.AdminUserUpdateRequest;
import com.velocity.api.user.dto.AdminUserUpdateRoleRequest;
import com.velocity.api.user.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin User Management", description = "Administrative endpoints for managing user accounts, access status, profiles, and roles")
@RequiredArgsConstructor
public class AdminUserController {
    private final AdminUserService adminUserService;

    @GetMapping("/users")
    @Operation(
            summary = "List all users",
            description = "Retrieves a paginated list of all registered users in the system."
    )
    public ResponseEntity<PaginatedResponse<AdminUserResponse>> listUsers(@ParameterObject Pageable pageable) {
        Page<AdminUserResponse> userPage = adminUserService.listUsers(pageable);
        PaginatedResponse<AdminUserResponse> response = PaginatedResponse.from(userPage);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/users/{id}/block")
    @Operation(
            summary = "Block a user",
            description = "Blocks the specified user account to prevent login and access. Records the ID of the administrator performing the action."
    )
    public ResponseEntity<Void> blockUser(@PathVariable("id") UUID id, @AuthenticationPrincipal CustomUserDetails userDetails) {
        adminUserService.blockUser(id, userDetails.getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/users/{id}/unblock")
    @Operation(
            summary = "Unblock a user",
            description = "Reactivates a previously blocked user account, restoring standard access."
    )
    public ResponseEntity<Void> unblockUser(@PathVariable("id") UUID id) {
        adminUserService.unblockUser(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/users/{id}")
    @Operation(
            summary = "Update user details",
            description = "Partially updates user profile fields (e.g., email, personal information) for the specified user ID."
    )
    public ResponseEntity<Void> updateUser(@PathVariable UUID id, @RequestBody AdminUserUpdateRequest request) {
        adminUserService.updateUser(id, request);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/users/{id}/role")
    @Operation(
            summary = "Update user role",
            description = "Modifies the role and system permissions of the target user. Records the ID of the administrator executing the change."
    )
    public ResponseEntity<Void> updateUserRole(@PathVariable UUID id, @Valid @RequestBody AdminUserUpdateRoleRequest request, @AuthenticationPrincipal CustomUserDetails userDetails) {
        adminUserService.updateUserRole(id, userDetails.getId(), request);
        return ResponseEntity.noContent().build();
    }
}
