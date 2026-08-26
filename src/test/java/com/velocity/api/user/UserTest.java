package com.velocity.api.user;

import com.velocity.api.common.City;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class UserTest {
    @Test
    public void updateProfile_validData_fieldsChange() {
        User user = User.registerClient("test@test.com", "hashed", "Test", "+48000000000", City.GDANSK);

        user.updateProfile("Changed", "+48111111111", City.POZNAN);

        assertThat(user.getFullName()).isEqualTo("Changed");
        assertThat(user.getPhone()).isEqualTo("+48111111111");
        assertThat(user.getCity()).isEqualByComparingTo(City.POZNAN);
    }

    @ParameterizedTest
    @MethodSource
    void updateProfile_invalidData_throwsException(String invalidName, String invalidPhone, City invalidCity, String expectedMessage) {
        User user = User.registerClient("test@test.com", "hashed", "Test", "+48000000000", City.GDANSK);

        assertThatThrownBy(() -> user.updateProfile(invalidName, invalidPhone, invalidCity)).hasMessageContaining(expectedMessage);
    }

    static Stream<Arguments> updateProfile_invalidData_throwsException() {
        return Stream.of(
                Arguments.of("", "+48123456789", City.GDANSK, "Full name is required"),
                Arguments.of("Test2", "-492329349834", City.GDANSK, "Phone must be a valid international format (e.g., +48123456789)"),
                Arguments.of("Test2", "+48123456789", null, "City is required")
        );
    }
}
