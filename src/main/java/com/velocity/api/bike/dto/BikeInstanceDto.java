package com.velocity.api.bike.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class BikeInstanceDto {
    private UUID id;
    private String status;
    private String city;
    private UUID bikeModelId;
}
