package com.velocity.api.reservation.controller;

import com.velocity.api.common.dto.PaginatedResponse;
import com.velocity.api.reservation.RentalPeriod;
import com.velocity.api.reservation.dto.*;
import com.velocity.api.reservation.service.ReservationService;
import com.velocity.api.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reservations")
@Tag(name = "Reservations", description = "Endpoints for checking availability, creating, confirming, canceling, and viewing bike reservations")
@RequiredArgsConstructor
public class ReservationController {
    private static final SortableFields SORTABLE = SortableFields.of("startDate", "endDate", "createdAt", "totalCost", "status");

    private final ReservationService reservationService;

    @PostMapping
    @Operation(
            summary = "Create a new reservation",
            description = "Reserves a bike in the user's own city for the selected dates. The reservation starts as PENDING and must be confirmed within 30 minutes."
    )
    public ResponseEntity<ReservationBookResponse> book(@Valid @RequestBody ReservationBookRequest request, @AuthenticationPrincipal CustomUserDetails userDetails) {
        ReservationBookResponse response = reservationService.book(userDetails.getId(), request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/availability")
    @Operation(
            summary = "Check available bike models",
            description = "Returns a list of bike models available within the specified date range and target city, taking into account the user's location."
    )
    public ResponseEntity<AvailabilityResponse> getAvailableModels(@RequestParam LocalDate startDate, @RequestParam LocalDate endDate, @AuthenticationPrincipal CustomUserDetails userDetails) {
        AvailabilityResponse response = reservationService.getAvailableModels(new RentalPeriod(startDate, endDate), userDetails.getCity());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/my")
    @Operation(
            summary = "Get user reservation history",
            description = "Fetches a paginated list of all reservations belonging to the authenticated user. Sortable by startDate, endDate, createdAt, totalCost and status; newest start date first by default."
    )
    public ResponseEntity<PaginatedResponse<ReservationResponse>> getUserReservations(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @ParameterObject @PageableDefault(size = 20, sort = "startDate", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<ReservationResponse> reservationsPage = reservationService.getUserReservations(userDetails.getId(), SORTABLE.check(pageable));
        PaginatedResponse<ReservationResponse> response = PaginatedResponse.from(reservationsPage);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get one reservation",
            description = "Fetches a single reservation. Requires ownership by the authenticated user; another user's reservation returns 404."
    )
    public ResponseEntity<ReservationResponse> getReservation(@PathVariable UUID id, @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(reservationService.getUserReservation(id, userDetails.getId()));
    }

    @PostMapping("/{id}/confirm")
    @Operation(
            summary = "Confirm a reservation",
            description = "Marks a pending reservation as confirmed. Requires ownership by the authenticated user, and is refused once the 30-minute confirmation window has passed."
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
