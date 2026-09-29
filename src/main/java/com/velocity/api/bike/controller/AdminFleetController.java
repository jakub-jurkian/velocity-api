package com.velocity.api.bike.controller;

import com.velocity.api.bike.BikeStatus;
import com.velocity.api.bike.dto.AdminBikeStatusUpdateRequest;
import com.velocity.api.bike.dto.BikeInstanceResponse;
import com.velocity.api.bike.service.FleetService;
import com.velocity.api.common.dto.PaginatedResponse;
import com.velocity.api.common.web.SortableFields;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/bikes")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Fleet Management", description = "Administrative endpoints for listing bike instances and changing their operational status")
@RequiredArgsConstructor
public class AdminFleetController {
    private static final SortableFields SORTABLE = SortableFields.of("id", "city", "status", "bikeModel.name");

    private final FleetService fleetService;

    @GetMapping
    @Operation(
            summary = "List fleet bike instances",
            description = "Retrieves a paginated list of all e-bike instances in the fleet, including their flattened model names. Supports optional filtering by current operational status (e.g., ACTIVE, MAINTENANCE). Sortable by id, city, status and bikeModel.name; ordered by id by default."
    )
    public ResponseEntity<PaginatedResponse<BikeInstanceResponse>> getBikes(
            @RequestParam Optional<BikeStatus> status,
            @ParameterObject @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        Page<BikeInstanceResponse> bikePage = fleetService.getBikes(status, SORTABLE.check(pageable));
        PaginatedResponse<BikeInstanceResponse> response = PaginatedResponse.from(bikePage);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/status")
    @Operation(
            summary = "Update bike operational status",
            description = "Transitions a specific bike instance to a new operational status. Enforces optimistic locking via the provided version number to prevent concurrent administrative modifications."
    )
    public ResponseEntity<Void> updateBikeStatus(@PathVariable("id") UUID id, @Valid @RequestBody AdminBikeStatusUpdateRequest request, @RequestParam(defaultValue = "false") boolean force) {
        fleetService.updateBikeStatus(id, request.status(), request.version(), force);
        return ResponseEntity.noContent().build();
    }
}
