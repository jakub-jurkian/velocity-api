package com.velocity.api.pricing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class RentalCostCalculator {
    private final BigDecimal dailyFlatRate;

    private static final int TIER_1_MAX_DAYS = 7;
    private static final int TIER_2_MAX_DAYS = 14;
    private static final BigDecimal TIER_2_MULTIPLIER = new BigDecimal("0.80");
    private static final BigDecimal TIER_3_MULTIPLIER = new BigDecimal("0.60");

    public RentalCostCalculator(@Value("${pricing.daily-rate}") BigDecimal dailyFlatRate) {
        if (dailyFlatRate.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("The flat rate must be a non-negative value.");
        }
        this.dailyFlatRate = dailyFlatRate;
    }

    public BigDecimal calculate(int rentalDays) {
        BigDecimal standardCost = this.dailyFlatRate.multiply(BigDecimal.valueOf(rentalDays));
        final BigDecimal multiplier;
        if (rentalDays <= 0) {
            throw new IllegalArgumentException("The rental days should be greater than 0.");
        } else if (rentalDays <= TIER_1_MAX_DAYS) {
            multiplier = BigDecimal.ONE;
        } else if (rentalDays <= TIER_2_MAX_DAYS) {
            multiplier = TIER_2_MULTIPLIER;
        } else {
            multiplier = TIER_3_MULTIPLIER;
        }
        return standardCost.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
    }
}
