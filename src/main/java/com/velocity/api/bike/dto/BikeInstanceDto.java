package com.velocity.api.bike.dto;

import com.velocity.api.bike.BikeStatus;
import com.velocity.api.shared.City;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class BikeInstanceDto {
    private UUID id;
    private BikeStatus status;
    private City city;
    private UUID bikeModelId;
}
