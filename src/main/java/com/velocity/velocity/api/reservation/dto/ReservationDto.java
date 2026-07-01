package com.velocity.velocity.api.reservation.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class ReservationDto {
    private UUID id;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private BigDecimal totalCost;
    private String status;
    private UUID userId;
    private UUID bikeInstanceId;
}
