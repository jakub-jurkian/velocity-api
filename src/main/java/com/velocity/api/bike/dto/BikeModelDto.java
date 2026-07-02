package com.velocity.api.bike.dto;

import com.velocity.api.bike.BikeCategory;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class BikeModelDto {
    private UUID id;
    private String name;
    private String description;
    private int speed;
    private int range;
    private int capacity;
    private BikeCategory category;
}
