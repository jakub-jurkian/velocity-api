package com.velocity.api.reservation.controller;

import com.velocity.api.reservation.dto.ReservationCreateRequest;
import com.velocity.api.reservation.dto.ReservationResponse;
import com.velocity.api.reservation.service.ReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reservations")
@RequiredArgsConstructor
public class ReservationController {
    private final ReservationService reservationService;

    @PostMapping
    public ResponseEntity<ReservationResponse> createReservation(
            // TODO: Ensure the incoming request payload is validated
            // TODO: Extract authenticated user ID instead of a random mock
            @Valid ReservationCreateRequest req
    ) {
        UUID authenticatedUserId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        ReservationResponse response = reservationService.createReservation(authenticatedUserId, req);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}
