package com.velocity.api.billing;

import java.math.BigDecimal;

public class RentalCostCalculator {
    public static BigDecimal calculate(int rentalDays, BigDecimal flatRate) {
        return flatRate.multiply(BigDecimal.valueOf(rentalDays));
    }
}
