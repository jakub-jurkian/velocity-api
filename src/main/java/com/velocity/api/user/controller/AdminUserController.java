package com.velocity.api.user.controller;

import com.velocity.api.common.dto.PaginatedResponse;
import com.velocity.api.user.dto.AdminUserResponse;
import com.velocity.api.user.dto.AdminUserUpdateRequest;
import com.velocity.api.user.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {
    private final AdminUserService adminUserService;

    @GetMapping("/users")
    public ResponseEntity<PaginatedResponse<AdminUserResponse>> listUsers(@ParameterObject Pageable pageable) {
        Page<AdminUserResponse> userPage = adminUserService.listUsers(pageable);
        PaginatedResponse<AdminUserResponse> response = PaginatedResponse.from(userPage);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/users/{id}/block")
    public ResponseEntity<Void> blockUser(@PathVariable("id") UUID id) {
        adminUserService.blockUser(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/users/{id}/unblock")
    public ResponseEntity<Void> unblockUser(@PathVariable("id") UUID id) {
        adminUserService.unblockUser(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/users/{id}/delete")
    public ResponseEntity<Void> softDeleteUser(@PathVariable("id") UUID id) {
        adminUserService.softDeleteUser(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/users/{id}")
    public ResponseEntity<Void> updateUser(@PathVariable UUID id, @RequestBody AdminUserUpdateRequest request) {
        adminUserService.updateUser(id, request);
        return ResponseEntity.noContent().build();
    }
}
