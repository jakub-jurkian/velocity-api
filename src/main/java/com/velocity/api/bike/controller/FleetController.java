package com.velocity.api.bike.controller;

import com.velocity.api.bike.dto.BikeInstanceDto;
import com.velocity.api.bike.service.FleetService;
import com.velocity.api.shared.dto.PaginatedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fleet")
@Tag(name = "Fleet Management", description = "Endpoints for interacting with the physical e-bike fleet")
public class FleetController {
    private final FleetService fleetService;

    public FleetController(FleetService fleetService) {
        this.fleetService = fleetService;
    }

    @GetMapping
    @Operation(
            summary = "Retrieve available bikes",
            description = "Returns a paginated list of all e-bikes currently marked with an ACTIVE status."
    )
    public ResponseEntity<PaginatedResponse<BikeInstanceDto>> getAvailableBikes(@ParameterObject Pageable pageable) {
        Page<BikeInstanceDto> bikePage = fleetService.getAvailableBikes(pageable);

        PaginatedResponse.PaginationMeta meta = new PaginatedResponse.PaginationMeta(
                bikePage.getNumber(),          // Current page number (0-indexed)
                bikePage.getSize(),            // Items per page
                bikePage.getTotalElements(),   // Total bikes in the DB
                bikePage.getTotalPages(),      // Total pages calculated by the DB
                bikePage.isFirst(),
                bikePage.isLast(),
                bikePage.hasNext(),
                bikePage.hasPrevious()
        );

        PaginatedResponse<BikeInstanceDto> response = new PaginatedResponse<>(
                bikePage.getContent(),
                meta
        );

        return ResponseEntity.ok(response);
    }
}
