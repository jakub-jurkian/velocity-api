package com.velocity.api.reservation.controller;

import com.velocity.api.common.dto.PaginatedResponse;
import com.velocity.api.reservation.dto.AvailableModelResponse;
import com.velocity.api.reservation.dto.ReservationBookRequest;
import com.velocity.api.reservation.dto.ReservationBookResponse;
import com.velocity.api.reservation.dto.ReservationResponse;
import com.velocity.api.reservation.service.ReservationService;
import com.velocity.api.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reservations")
@RequiredArgsConstructor
public class ReservationController {
    private final ReservationService reservationService;

    @PostMapping
    public ResponseEntity<ReservationBookResponse> book(
            @Valid @RequestBody ReservationBookRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        UUID authenticatedUserId = userDetails.getId();
        ReservationBookResponse response = reservationService.book(authenticatedUserId, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/availability")
    public ResponseEntity<List<AvailableModelResponse>> getAvailableModels(@RequestParam LocalDate startDate, @RequestParam LocalDate endDate) {
        List<AvailableModelResponse> response = reservationService.getAvailableModels(startDate, endDate);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/my")
    public ResponseEntity<PaginatedResponse<ReservationResponse>> getUserReservations(@AuthenticationPrincipal CustomUserDetails userDetails, Pageable pageable) {
        UUID authenticatedUserId = userDetails.getId();
        Page<ReservationResponse> reservationsPage = reservationService.getUserReservations(authenticatedUserId, pageable);
        PaginatedResponse<ReservationResponse> response = PaginatedResponse.from(reservationsPage);
        return ResponseEntity.ok(response);
    }
}
