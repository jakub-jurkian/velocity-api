package com.velocity.api.billing;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


public class RentalCostCalculatorTest {
    @Test
    // Information about business logic behind the method
    @DisplayName("Cost is exactly the flat rate multiplied by the number of rental days")
    //MethodName_State_Expectation
    public void calculate_positiveDaysAndRate_returnsMultipliedTotal() {
        // 1. Arrange
        int rentalDays = 5;
        BigDecimal flatRate = new BigDecimal("15.50");
        BigDecimal expectedCost = new BigDecimal("77.50");
        // 2. Act
        BigDecimal actualCost = RentalCostCalculator.calculate(rentalDays, flatRate);
        // 3. Assert
        assertEquals(expectedCost, actualCost);
    }

    @Test
    @DisplayName("A flat rate of zero computes to a total cost of zero (Promotional Day)")
    public void calculate_zeroFlatRate_returnsZero() {
        int rentalDays = 3;
        BigDecimal flatRate = BigDecimal.ZERO;
        BigDecimal expectedCost = BigDecimal.ZERO;

        BigDecimal actualCost = RentalCostCalculator.calculate(rentalDays, flatRate);

        assertEquals(expectedCost, actualCost);
    }

    @Test
    @DisplayName("Clients cannot rent ebikes for zero or negative days")
    public void calculate_notPositiveDays_throwsIllegalArgumentException() {
        // Arrange
        int rentalDays = 0;
        BigDecimal flatRate = new BigDecimal("15.50");

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            RentalCostCalculator.calculate(rentalDays, flatRate);
        });
    }

    @Test
    @DisplayName("Company has to have profit from renting ebikes")
    public void calculate_negativeFlatRate_throwsIllegalArgumentException() {
        int rentalDays = 5;
        BigDecimal flatRate = new BigDecimal("-1");
        assertThrows(IllegalArgumentException.class, () -> {
            RentalCostCalculator.calculate(rentalDays, flatRate);
        });
    }
}
