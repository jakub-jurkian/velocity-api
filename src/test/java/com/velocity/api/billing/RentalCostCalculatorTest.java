package com.velocity.api.billing;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;


public class RentalCostCalculatorTest {
    @Test
    // Information about business logic behind the method
    @DisplayName("Cost is exactly the flat rate multiplied by the number of rental days")
    //MethodName_State_Expectation
    public void calculate_validInputs_returnsCost() {
        // 1. Arrange
        int rentalDays = 5;
        BigDecimal flatRate = new BigDecimal("15.50");
        BigDecimal expectedCost = new BigDecimal("77.50");
        // 2. Act
        BigDecimal actualCost = RentalCostCalculator.calculate(rentalDays, flatRate);
        // 3. Assert
        assertEquals(expectedCost, actualCost);
    }
}
