package com.velocity.api.bike.controller;

import com.velocity.api.bike.dto.BikeInstanceCountResponse;
import com.velocity.api.bike.service.FleetService;
import com.velocity.api.common.City;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fleet")
@RequiredArgsConstructor
public class FleetController {
    private final FleetService fleetService;

    @GetMapping("/count")
    public ResponseEntity<BikeInstanceCountResponse> getActiveBikesCount(@RequestParam("city") City city) {
        BikeInstanceCountResponse response = fleetService.getActiveBikesCount(city);
        return ResponseEntity.ok(response);
    }
}
