package com.velocity.api.reservation.controller;

import com.velocity.api.reservation.dto.AvailableModelResponse;
import com.velocity.api.reservation.dto.ReservationCreateRequest;
import com.velocity.api.reservation.dto.ReservationResponse;
import com.velocity.api.reservation.service.ReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.function.EntityResponse;

import java.time.LocalDate;
import java.util.List;
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
            @Valid @RequestBody ReservationCreateRequest req
    ) {
        UUID authenticatedUserId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        ReservationResponse response = reservationService.createReservation(authenticatedUserId, req);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/availability")
    public ResponseEntity<List<AvailableModelResponse>> getAvailableModels(@RequestParam LocalDate startDate, @RequestParam LocalDate endDate) {
        List<AvailableModelResponse> response = reservationService.getAvailableModels(startDate, endDate);
        return ResponseEntity.ok(response);
    }
}
