package com.velocity.api.analytics.service;

import com.velocity.api.analytics.dto.DashboardMetricsResponse;
import com.velocity.api.bike.repository.BikeInstanceRepository;
import com.velocity.api.reservation.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AnalyticsService {
    private final ReservationRepository reservationRepository;
    private final BikeInstanceRepository bikeInstanceRepository;

    public DashboardMetricsResponse getAnalytics() {
        BigDecimal totalRevenue = reservationRepository.findTotalRevenue();
        if (totalRevenue == null) totalRevenue = BigDecimal.ZERO;

        long activeRentals = reservationRepository.countActiveRentals();
        List<DashboardMetricsResponse.RevenueByMonth> revenueTrend = reservationRepository.findRevenueTrend();
        List<DashboardMetricsResponse.FleetPopularity> fleetPopularity = reservationRepository.findFleetPopularity();

        long totalFleetSize = bikeInstanceRepository.count();
        long occupancyRate;
        if (totalFleetSize == 0) occupancyRate = 0;
        else occupancyRate = (activeRentals * 100) / totalFleetSize;


        return new DashboardMetricsResponse(
                totalRevenue,
                activeRentals,
                occupancyRate,
                revenueTrend,
                fleetPopularity
        );
    }
}
