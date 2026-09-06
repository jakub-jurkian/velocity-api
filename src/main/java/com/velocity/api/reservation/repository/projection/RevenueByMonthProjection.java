package com.velocity.api.reservation.repository.projection;

import java.math.BigDecimal;

public interface RevenueByMonthProjection {
    int getYear();

    int getMonth();

    BigDecimal getRevenue();
}
