package com.velocity.api.bike;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Entity
@Table(name = "bike_models")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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
    @OneToMany(mappedBy = "bikeModel")
    private final List<BikeInstance> bikeInstances = new ArrayList<>();

    public static BikeModel create(String name, String description, int speed, int range, int capacity, BikeCategory category) {
        return new BikeModel(name, description, speed, range, capacity, category);
    }

    private BikeModel(String name, String description, int speed, int range, int capacity, BikeCategory category) {
        this.name = name;
        this.description = description;
        this.speed = speed;
        this.range = range;
        this.capacity = capacity;
        this.category = category;
    }
}
