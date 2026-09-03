package com.velocity.api.pricing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class RentalCostCalculator {
    private final BigDecimal dailyFlatRate;

    public RentalCostCalculator(@Value("${pricing.daily-rate}") BigDecimal dailyFlatRate) {
        if (dailyFlatRate.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("The flat rate should be greater a non-negative value.");
        }
        this.dailyFlatRate = dailyFlatRate;
    }

    public BigDecimal calculate(int rentalDays) {
        BigDecimal standardCost = this.dailyFlatRate.multiply(BigDecimal.valueOf(rentalDays));
        final BigDecimal multiplier;
        if (rentalDays <= 0) {
            throw new IllegalArgumentException("The rental days should be greater than 0.");
        } else if (rentalDays <= 7) {
            multiplier = BigDecimal.ONE;
        } else if (rentalDays <= 14) {
            multiplier = new BigDecimal("0.80");
        } else {
            multiplier = new BigDecimal("0.60");
        }
        return standardCost.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
    }
}
