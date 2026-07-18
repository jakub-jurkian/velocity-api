package com.velocity.api.billing;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
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
        RentalCostCalculator calculator = new RentalCostCalculator(flatRate);
        BigDecimal expectedCost = new BigDecimal("77.50");
        // 2. Act
        BigDecimal actualCost = calculator.calculate(rentalDays);
        // 3. Assert
        assertThat(actualCost).isEqualByComparingTo(expectedCost);
    }

    @Test
    @DisplayName("A flat rate of zero computes to a total cost of zero (Promotional Day)")
    public void calculate_zeroFlatRate_returnsZero() {
        int rentalDays = 3;
        RentalCostCalculator calculator = new RentalCostCalculator(BigDecimal.ZERO);

        BigDecimal actualCost = calculator.calculate(rentalDays);

        assertThat(actualCost).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Clients cannot rent ebikes for zero or negative days")
    public void calculate_notPositiveDays_throwsIllegalArgumentException() {
        // Arrange
        int rentalDays = 0;
        RentalCostCalculator calculator = new RentalCostCalculator(new BigDecimal("15.50"));

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            calculator.calculate(rentalDays);
        });
    }
}
