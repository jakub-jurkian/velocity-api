package com.velocity.api.bike;

import com.velocity.api.reservation.Reservation;
import com.velocity.api.common.City;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Entity
@Table(name = "bike_instances")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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

    @OneToMany(mappedBy = "bikeInstance")
    private final List<Reservation> reservations = new ArrayList<>();

    public static BikeInstance initialize(BikeModel bikeModel, City city) {
        return new BikeInstance(bikeModel, city);
    }

    private BikeInstance(BikeModel bikeModel, City city) {
        this.bikeModel = bikeModel;
        this.city = city;
        this.status = BikeStatus.ACTIVE; // Default state for a new physical bike
    }
}
