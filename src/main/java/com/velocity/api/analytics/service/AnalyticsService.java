package com.velocity.api.analytics.service;

import com.velocity.api.analytics.dto.DashboardMetricsResponse;
import com.velocity.api.bike.BikeStatus;
import com.velocity.api.bike.repository.BikeInstanceRepository;
import com.velocity.api.reservation.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AnalyticsService {
    private final ReservationRepository reservationRepository;
    private final BikeInstanceRepository bikeInstanceRepository;

    @Transactional(readOnly = true)
    public DashboardMetricsResponse getDashboardMetrics() {
        BigDecimal totalRevenue = reservationRepository.findTotalRevenue();

        long activeRentals = reservationRepository.countActiveRentals();

        List<DashboardMetricsResponse.MonthlyRevenue> revenueTrend = reservationRepository.findRevenueTrend().stream()
                .map(r -> new DashboardMetricsResponse.MonthlyRevenue(r.getYear(), r.getMonth(), r.getRevenue()))
                .toList();
        List<DashboardMetricsResponse.FleetPopularity> fleetPopularity = reservationRepository.
                findFleetPopularity().stream().map(r -> new DashboardMetricsResponse.FleetPopularity(r.getModelName(), r.getCount())).toList();

        long totalActiveFleetSize = bikeInstanceRepository.countByStatus(BikeStatus.ACTIVE);

        double occupancyRate;
        if (totalActiveFleetSize == 0) occupancyRate = 0.0;
        else occupancyRate = (((double) activeRentals * 100) / totalActiveFleetSize);

        return new DashboardMetricsResponse(
                totalRevenue,
                activeRentals,
                occupancyRate,
                revenueTrend,
                fleetPopularity
        );
    }
}
