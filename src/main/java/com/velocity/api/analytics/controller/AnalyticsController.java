package com.velocity.api.analytics.controller;

import com.velocity.api.analytics.dto.DashboardMetricsResponse;
import com.velocity.api.analytics.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/analytics")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AnalyticsController {
    private final AnalyticsService analyticsService;

    @GetMapping
    public ResponseEntity<DashboardMetricsResponse> getAnalytics() {
        DashboardMetricsResponse response = analyticsService.getAnalytics();
        return ResponseEntity.ok(response);
    }
}
