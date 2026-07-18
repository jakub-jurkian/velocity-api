package com.velocity.api.billing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class RentalCostCalculator {
    private final BigDecimal dailyFlatRate;

    public RentalCostCalculator(@Value("${pricing.daily-rate}") BigDecimal dailyFlatRate) {
        this.dailyFlatRate = dailyFlatRate;
    }

    public BigDecimal calculate(int rentalDays) {
        if(rentalDays <= 0) {
            throw new IllegalArgumentException("The rental days should be greater than 0.");
        }

        if(dailyFlatRate.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("The flat rate should be greater a non-negative value.");
        }

        return this.dailyFlatRate.multiply(BigDecimal.valueOf(rentalDays));
    }
}
