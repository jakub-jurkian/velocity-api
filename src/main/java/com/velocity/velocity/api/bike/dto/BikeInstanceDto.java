package com.velocity.velocity.api.bike.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
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
