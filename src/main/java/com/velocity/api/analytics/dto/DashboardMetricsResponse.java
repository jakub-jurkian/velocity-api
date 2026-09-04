package com.velocity.api.analytics.dto;

import java.math.BigDecimal;
import java.util.List;

public record DashboardMetricsResponse(
        BigDecimal totalRevenue,
        long activeRentals,
        long occupancyRate,
        List<RevenueByMonth> revenueTrend,
        List<FleetPopularity> popularityStats
) {
    public interface RevenueByMonth {
        int getYear();

        int getMonth();

        BigDecimal getRevenue();
    }

    public interface FleetPopularity {
        String getModelName();

        long getCount();
    }
}
