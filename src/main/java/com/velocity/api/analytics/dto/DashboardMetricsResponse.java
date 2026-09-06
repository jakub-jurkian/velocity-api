package com.velocity.api.analytics.dto;

import java.math.BigDecimal;
import java.util.List;

public record DashboardMetricsResponse(
        BigDecimal totalRevenue,
        long activeRentals,
        Double occupancyRate,
        List<MonthlyRevenue> revenueTrend,
        List<FleetPopularity> popularityStats
) {
    public record MonthlyRevenue(int year, int month, BigDecimal revenue) {
    }

    public record FleetPopularity(String modelName, long count) {
    }
}
