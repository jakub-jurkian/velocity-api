package com.velocity.api.bike;

import com.velocity.api.reservation.Reservation;
import com.velocity.api.shared.City;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "bike_instances")
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

    @OneToMany(mappedBy = "bikeInstance", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Reservation> reservations = new ArrayList<>();
}
