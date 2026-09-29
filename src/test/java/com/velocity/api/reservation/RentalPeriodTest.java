package com.velocity.api.reservation;

import com.velocity.api.common.exception.DomainValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RentalPeriodTest {
    private static final LocalDate START = LocalDate.parse("2026-10-10");

    @ParameterizedTest
    @ValueSource(ints = {3, 7, 21})
    void allowedLength_isAccepted_andEndDateIsExclusive(int days) {
        RentalPeriod period = new RentalPeriod(START, START.plusDays(days));

        assertThat(period.days()).isEqualTo(days);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 22, 30})
    void lengthOutsideThreeToTwentyOneDays_isRejected(int days) {
        assertThatThrownBy(() -> new RentalPeriod(START, START.plusDays(days)))
                .isInstanceOf(DomainValidationException.class)
                .hasMessage("Rental duration must be between 3 and 21 days.");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -3})
    void endDateNotAfterStartDate_isRejected(int offset) {
        assertThatThrownBy(() -> new RentalPeriod(START, START.plusDays(offset)))
                .isInstanceOf(DomainValidationException.class)
                .hasMessage("End date must be after start date.");
    }

    @Test
    void missingDate_isRejected() {
        assertThatThrownBy(() -> new RentalPeriod(null, START))
                .isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> new RentalPeriod(START, null))
                .isInstanceOf(DomainValidationException.class);
    }
}
