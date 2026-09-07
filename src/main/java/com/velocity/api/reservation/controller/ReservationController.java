package com.velocity.api.reservation.controller;

import com.velocity.api.common.City;
import com.velocity.api.common.dto.PaginatedResponse;
import com.velocity.api.reservation.dto.AvailableModelResponse;
import com.velocity.api.reservation.dto.ReservationBookRequest;
import com.velocity.api.reservation.dto.ReservationBookResponse;
import com.velocity.api.reservation.dto.ReservationResponse;
import com.velocity.api.reservation.service.ReservationService;
import com.velocity.api.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Reservations", description = "Endpoints for checking availability, creating, confirming, canceling, and viewing bike reservations")
@RequiredArgsConstructor
public class ReservationController {
    private final ReservationService reservationService;

    @PostMapping
    @Operation(
            summary = "Create a new reservation",
            description = "Reserves a bike for the authenticated user based on the selected dates, model, and location."
    )
    public ResponseEntity<ReservationBookResponse> book(@Valid @RequestBody ReservationBookRequest request, @AuthenticationPrincipal CustomUserDetails userDetails) {
        ReservationBookResponse response = reservationService.book(userDetails.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/availability")
    @Operation(
            summary = "Check available bike models",
            description = "Returns a list of bike models available within the specified date range and target city, taking into account the user's location."
    )
    public ResponseEntity<List<AvailableModelResponse>> getAvailableModels(@RequestParam LocalDate startDate, @RequestParam LocalDate endDate, @RequestParam City city, @AuthenticationPrincipal CustomUserDetails userDetails) {
        List<AvailableModelResponse> response = reservationService.getAvailableModels(startDate, endDate, city, userDetails.getCity());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/my")
    @Operation(
            summary = "Get user reservation history",
            description = "Fetches a paginated list of all reservations belonging to the authenticated user."
    )
    public ResponseEntity<PaginatedResponse<ReservationResponse>> getUserReservations(@AuthenticationPrincipal CustomUserDetails userDetails, Pageable pageable) {
        Page<ReservationResponse> reservationsPage = reservationService.getUserReservations(userDetails.getId(), pageable);
        PaginatedResponse<ReservationResponse> response = PaginatedResponse.from(reservationsPage);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/confirm")
    @Operation(
            summary = "Confirm a reservation",
            description = "Marks a pending reservation as confirmed for the specified reservation ID. Requires ownership by the authenticated user."
    )
    public ResponseEntity<Void> confirmReservation(@PathVariable UUID id, @AuthenticationPrincipal CustomUserDetails userDetails) {
        reservationService.confirmReservation(id, userDetails.getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/cancel")
    @Operation(
            summary = "Cancel a reservation",
            description = "Cancels an existing reservation by ID, freeing the allocated bike slot. Requires ownership by the authenticated user."
    )
    public ResponseEntity<Void> cancelReservation(@PathVariable UUID id, @AuthenticationPrincipal CustomUserDetails userDetails) {
        reservationService.cancelReservation(id, userDetails.getId());
        return ResponseEntity.noContent().build();
    }
}
