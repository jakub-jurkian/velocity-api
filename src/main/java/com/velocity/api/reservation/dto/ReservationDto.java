package com.velocity.api.reservation.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class ReservationDto {
    private UUID id;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal totalCost;
    private String status;
    private UUID userId;
    private UUID bikeInstanceId;
}
