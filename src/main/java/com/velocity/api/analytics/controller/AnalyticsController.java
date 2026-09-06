package com.velocity.api.analytics.controller;

import com.velocity.api.analytics.dto.DashboardMetricsResponse;
import com.velocity.api.analytics.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/analytics")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Analytics", description = "Fleet operational analytics")
@RequiredArgsConstructor
public class AnalyticsController {
    private final AnalyticsService analyticsService;

    @GetMapping
    @Operation(
            summary = "Retrieve platform dashboard analytics",
            description = "Fetches global platform metrics including total revenue, occupancy rate, active rentals, revenue trend, and fleet popularity."
    )
    public ResponseEntity<DashboardMetricsResponse> getDashboardMetrics() {
        DashboardMetricsResponse response = analyticsService.getDashboardMetrics();
        return ResponseEntity.ok(response);
    }
}
