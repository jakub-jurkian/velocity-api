package com.velocity.api.pricing.dto;

import java.math.BigDecimal;

public record RentalQuote(
        int rentalDays,
        BigDecimal baseDailyRate,
        BigDecimal effectiveDailyRate,
        int discountPercentage,
        BigDecimal totalCost
) {
}
