package com.velocity.api.analytics.dto;

import com.velocity.api.reservation.repository.projection.FleetPopularityProjection;
import com.velocity.api.reservation.repository.projection.RevenueByMonthProjection;

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
        public static MonthlyRevenue from(RevenueByMonthProjection monthlyRevenue) {
            return new MonthlyRevenue(monthlyRevenue.getYear(), monthlyRevenue.getMonth(), monthlyRevenue.getRevenue());
        }
    }

    public record FleetPopularity(String modelName, long count) {
        public static FleetPopularity from(FleetPopularityProjection fleetPopularity) {
            return new FleetPopularity(fleetPopularity.getModelName(), fleetPopularity.getCount());
        }
    }
}
