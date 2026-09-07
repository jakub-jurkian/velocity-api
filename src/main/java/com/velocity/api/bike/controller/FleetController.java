package com.velocity.api.bike.controller;

import com.velocity.api.bike.BikeStatus;
import com.velocity.api.bike.dto.BikeInstanceCountResponse;
import com.velocity.api.bike.service.FleetService;
import com.velocity.api.common.City;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fleet")
@Tag(name = "Fleet Management", description = "Inventory and physical status tracking of the e-bike fleet")
@RequiredArgsConstructor
public class FleetController {
    private final FleetService fleetService;

    @GetMapping("/count")
    @Operation(summary = "Get fleet size by status and city", description = "Aggregates the total number of bikes in a specific city matching the requested operational status.")
    public ResponseEntity<BikeInstanceCountResponse> getBikesCount(@RequestParam("city") City city, BikeStatus status) {
        BikeInstanceCountResponse response = fleetService.getBikesCount(city, status);
        return ResponseEntity.ok(response);
    }
}
