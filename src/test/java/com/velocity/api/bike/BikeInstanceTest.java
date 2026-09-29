package com.velocity.api.bike;

import com.velocity.api.bike.exception.BikeInOtherCityException;
import com.velocity.api.bike.exception.InvalidBikeStateException;
import com.velocity.api.common.City;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BikeInstanceTest {

    @Test
    void assertBookableIn_activeBikeInClientsCity_passes() {
        BikeInstance bike = bikeIn(City.WARSAW);

        assertThatCode(() -> bike.assertBookableIn(City.WARSAW)).doesNotThrowAnyException();
    }

    @Test
    void assertBookableIn_bikeInAnotherCity_isRefused() {
        BikeInstance bike = bikeIn(City.GDANSK);

        assertThatThrownBy(() -> bike.assertBookableIn(City.WARSAW))
                .isInstanceOf(BikeInOtherCityException.class);
    }

    @ParameterizedTest
    @EnumSource(value = BikeStatus.class, names = {"MAINTENANCE", "LOST"})
    void assertBookableIn_bikeNotActive_isRefused(BikeStatus status) {
        BikeInstance bike = bikeIn(City.WARSAW);
        bike.transitionTo(status);

        assertThatThrownBy(() -> bike.assertBookableIn(City.WARSAW))
                .isInstanceOf(InvalidBikeStateException.class);
    }

    private static BikeInstance bikeIn(City city) {
        BikeModel model = BikeModel.create("Test Model", "Test description", 25, 60, 40, BikeCategory.AGILITY);
        return BikeInstance.initialize(model, city);
    }
}
