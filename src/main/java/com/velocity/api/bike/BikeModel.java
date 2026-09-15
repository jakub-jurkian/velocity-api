package com.velocity.api.bike;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "bike_models")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
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

    public static BikeModel create(String name, String description, int speed, int range, int capacity, BikeCategory category) {
        return new BikeModel(name, description, speed, range, capacity, category);
    }

    private BikeModel(String name, String description, int speed, int range, int capacity, BikeCategory category) {
        validateCategory(category);
        validateSpeed(speed);
        validateRange(range);
        validateCapacity(capacity);
        this.name = normalizeAndValidateName(name);
        this.description = normalizeAndValidateDescription(description);
        this.speed = speed;
        this.range = range;
        this.capacity = capacity;
        this.category = category;
    }

    private String normalizeAndValidateName(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Name is required");
        String sanitizedName = name.trim();
        if (sanitizedName.length() < 2 || sanitizedName.length() > 60) {
            throw new IllegalArgumentException("Name length must be between 2 and 60.");
        }
        return sanitizedName;
    }

    private String normalizeAndValidateDescription(String description) {
        if (description == null || description.isBlank()) throw new IllegalArgumentException("Description is required");
        String sanitizedDescription = description.trim();
        if (sanitizedDescription.length() < 2 || sanitizedDescription.length() > 255) {
            throw new IllegalArgumentException("Description length must be between 2 and 255.");
        }
        return sanitizedDescription;
    }

    private void validateCategory(BikeCategory bikeCategory) {
        if (bikeCategory == null) throw new IllegalArgumentException("Bike category is required");
    }

    private void validateSpeed(int speed) {
        if (speed <= 0 || speed > 45)
            throw new IllegalArgumentException("Speed must be between 1 and 45.");
    }

    private void validateRange(int range) {
        if (range < 15 || range > 500)
            throw new IllegalArgumentException("Range must be between 15 and 500.");
    }
    private void validateCapacity(int capacity) {
        if (capacity <= 0 || capacity > 100)
            throw new IllegalArgumentException("Capacity must be between 1 and 100.");
    }
}
