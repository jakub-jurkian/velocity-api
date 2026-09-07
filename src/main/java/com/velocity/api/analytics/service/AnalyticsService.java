package com.velocity.api.analytics.service;

import com.velocity.api.analytics.dto.DashboardMetricsResponse;
import com.velocity.api.bike.BikeStatus;
import com.velocity.api.bike.repository.BikeInstanceRepository;
import com.velocity.api.reservation.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AnalyticsService {
    private final ReservationRepository reservationRepository;
    private final BikeInstanceRepository bikeInstanceRepository;

    @Transactional(readOnly = true)
    public DashboardMetricsResponse getDashboardMetrics() {
        long activeRentals = reservationRepository.countActiveRentals();
        long totalActiveFleetSize = bikeInstanceRepository.countByStatus(BikeStatus.ACTIVE);
        BigDecimal totalRevenue = reservationRepository.findTotalRevenue();
        List<DashboardMetricsResponse.MonthlyRevenue> revenueTrend = reservationRepository.findRevenueTrend().stream()
                .map(DashboardMetricsResponse.MonthlyRevenue::from).toList();
        List<DashboardMetricsResponse.FleetPopularity> fleetPopularity = reservationRepository.
                findFleetPopularity().stream().map(DashboardMetricsResponse.FleetPopularity::from).toList();

        double occupancyRate = (totalActiveFleetSize == 0) ? 0.0 :
                BigDecimal.valueOf(((double) activeRentals * 100) / totalActiveFleetSize)
                        .setScale(2, RoundingMode.HALF_UP)
                        .doubleValue();

        return new DashboardMetricsResponse(totalRevenue, activeRentals, occupancyRate, revenueTrend, fleetPopularity);
    }
}
