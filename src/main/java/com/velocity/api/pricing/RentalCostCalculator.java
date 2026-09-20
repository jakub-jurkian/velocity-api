package com.velocity.api.pricing;

import com.velocity.api.common.exception.DomainValidationException;
import com.velocity.api.pricing.dto.RentalQuote;
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

    public RentalCostCalculator(PricingProperties pricing) {
        this.dailyFlatRate = pricing.dailyRate();
    }

    public RentalQuote calculateQuote(int rentalDays) {
        if (rentalDays <= 0) {
            throw new DomainValidationException("The rental days should be greater than 0.");
        }
        BigDecimal standardCost = this.dailyFlatRate.multiply(BigDecimal.valueOf(rentalDays));
        final BigDecimal multiplier;
        if (rentalDays <= TIER_1_MAX_DAYS) {
            multiplier = BigDecimal.ONE;
        } else if (rentalDays <= TIER_2_MAX_DAYS) {
            multiplier = TIER_2_MULTIPLIER;
        } else {
            multiplier = TIER_3_MULTIPLIER;
        }
        BigDecimal totalCost = standardCost.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
        int discountPercentage = 100 - multiplier.multiply(BigDecimal.valueOf(100)).intValue();
        BigDecimal effectiveDailyRate = this.dailyFlatRate.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
        return new RentalQuote(rentalDays, this.dailyFlatRate, effectiveDailyRate, discountPercentage, totalCost);
    }
}
