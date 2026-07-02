package com.velocity.api.bike;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "bike_models")
public class BikeModel {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false, unique = true)
    private String name;
    @Column(nullable = false)
    private String description;
    @Column(nullable = false)
    private int speed;
    @Column(nullable = false)
    private int range;
    @Column(nullable = false)
    private int capacity;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BikeCategory category;
    @OneToMany(mappedBy = "bikeModel", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BikeInstance> bikeInstances = new ArrayList<>();
}
