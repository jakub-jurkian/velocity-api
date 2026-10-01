package com.velocity.api.bike;

import com.velocity.api.bike.exception.BikeInOtherCityException;
import com.velocity.api.bike.exception.InvalidBikeStateException;
import com.velocity.api.bike.exception.InvalidBikeStatusTransitionException;
import com.velocity.api.common.City;
import com.velocity.api.common.exception.DomainValidationException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;


@Entity
@Table(name = "bike_instances")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class BikeInstance {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private BikeStatus status;
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private City city;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bike_model_id", nullable = false)
    private BikeModel bikeModel;

    @Version
    private Long version;

    public static BikeInstance initialize(BikeModel bikeModel, City city) {
        return new BikeInstance(bikeModel, city);
    }

    private BikeInstance(BikeModel bikeModel, City city) {
        this.bikeModel = requireNonNull(bikeModel, "BikeModel");
        this.city = requireNonNull(city, "City");
        this.status = BikeStatus.ACTIVE; // Default state for a new physical bike
    }


    // Whether a client from clientCity may book this bike. Date availability is a
    // separate question, answered by the reservations table.
    public void assertBookableIn(City clientCity) {
        if (this.status != BikeStatus.ACTIVE) {
            throw new InvalidBikeStateException("The bike does not have ACTIVE status.");
        }
        if (this.city != clientCity) {
            throw new BikeInOtherCityException("The bike is not available in your city.");
        }
    }

    public void transitionTo(BikeStatus newStatus) {
        if (this.status == newStatus) return;

        if (this.status == BikeStatus.RETIRED) {
            throw new InvalidBikeStatusTransitionException("A retired bike cannot change status.");
        }

        if (this.status == BikeStatus.LOST && (newStatus == BikeStatus.ACTIVE || newStatus == BikeStatus.RETIRED)) {
            throw new InvalidBikeStatusTransitionException("Found bikes must go to MAINTENANCE first.");
        }

        this.status = newStatus;
    }

    private <T> T requireNonNull(T value, String fieldName) {
        if (value == null) {
            throw new DomainValidationException(fieldName + " is required.");
        }
        return value;
    }
}
