package com.velocity.velocity.api.bike;

import com.velocity.velocity.api.reservation.Reservation;
import com.velocity.velocity.api.user.UserCity;
import com.velocity.velocity.api.user.UserRole;
import com.velocity.velocity.api.user.UserStatus;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "bike_instances")
public class BikeInstance {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Enumerated(EnumType.STRING)
    private BikeStatus status;
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private UserCity city;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bike_model_id", nullable = false)
    private BikeModel bikeModel;

    @OneToMany(mappedBy = "bikeInstance", cascade = CascadeType.ALL, orphanRemoval = true)
    List<Reservation> reservations = new ArrayList<>();
}
