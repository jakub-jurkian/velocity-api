package com.velocity.api.billing;

import java.math.BigDecimal;

public class RentalCostCalculator {
    public static BigDecimal calculate(int rentalDays, BigDecimal flatRate) {
        if(rentalDays <= 0) {
            throw new IllegalArgumentException("The rental days should be greater than 0.");
        }

        if(flatRate.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("The flat rate should be greater a non-negative value.");
        }

        return flatRate.multiply(BigDecimal.valueOf(rentalDays));
    }
}
