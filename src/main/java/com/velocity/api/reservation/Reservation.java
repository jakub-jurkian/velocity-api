package com.velocity.api.reservation;

import com.velocity.api.bike.BikeInstance;
import com.velocity.api.common.exception.DomainValidationException;
import com.velocity.api.reservation.exception.InvalidStatusTransitionException;
import com.velocity.api.reservation.exception.LateCancelException;
import com.velocity.api.reservation.exception.ReservationExpiredException;
import com.velocity.api.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "reservations")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Reservation {
    // How long a PENDING reservation holds its bike without being confirmed. After that it
    // can no longer be confirmed, and the scheduler expires it.
    public static final Duration CONFIRMATION_WINDOW = Duration.ofMinutes(30);
    static final String EXPIRED_REASON = "Not confirmed within " + CONFIRMATION_WINDOW.toMinutes() + " minutes";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false)
    private LocalDate startDate;
    @Column(nullable = false)
    private LocalDate endDate;
    @Column(nullable = false)
    private BigDecimal totalCost;
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ReservationStatus status;
    private String cancellationReason;
    @Column(nullable = false, updatable = false)
    @CreatedDate
    private Instant createdAt;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bike_instance_id", nullable = false)
    private BikeInstance bikeInstance;

    @Version
    private Long version;

    public void transitionTo(ReservationStatus newStatus, LocalDate currentDate) {
        if (this.status == newStatus) return;

        assertTransitionAllowed(newStatus);

        if (newStatus.equals(ReservationStatus.CANCELLED) && !currentDate.isBefore(this.startDate)) {
            throw new LateCancelException("The reservation cannot be cancelled on or after the start date.");
        }

        this.status = newStatus;
    }

    // The customer's confirmation. Refused once the confirmation window has passed, even if
    // the scheduler has not expired the reservation yet.
    public void confirm(Instant now) {
        if (this.status == ReservationStatus.CONFIRMED) return;

        assertTransitionAllowed(ReservationStatus.CONFIRMED);

        if (isConfirmationWindowOver(now)) {
            throw new ReservationExpiredException(
                    "This reservation was not confirmed within " + CONFIRMATION_WINDOW.toMinutes()
                            + " minutes and has expired. Please book again.");
        }

        this.status = ReservationStatus.CONFIRMED;
    }


    // System cancellation of a reservation nobody confirmed. Not the customer's cancel: the
    // late-cancel rule does not apply, because a booking made late in the evening for the next
    // day only goes stale after midnight, when its start date is already today.
    // A no-op when the reservation is no longer PENDING - the customer confirmed or
    // canceled it between the scheduler's query and this call.
    public void expire(Instant now) {
        if (this.status != ReservationStatus.PENDING) return;

        if (!isConfirmationWindowOver(now)) {
            throw new DomainValidationException("The reservation is still within its confirmation window.");
        }

        this.cancellationReason = EXPIRED_REASON;
        this.status = ReservationStatus.CANCELLED;
    }

    private boolean isConfirmationWindowOver(Instant now) {
        // createdAt is null only before the first persist, when nothing can have expired yet.
        return this.createdAt != null && !now.isBefore(this.createdAt.plus(CONFIRMATION_WINDOW));
    }

    private void assertTransitionAllowed(ReservationStatus newStatus) {
        boolean isValid = switch (this.status) {
            case PENDING -> newStatus == ReservationStatus.CONFIRMED || newStatus == ReservationStatus.CANCELLED;
            case CONFIRMED -> newStatus == ReservationStatus.COMPLETED || newStatus == ReservationStatus.CANCELLED;
            case CANCELLED, COMPLETED -> false;
        };

        if (!isValid) {
            throw new InvalidStatusTransitionException(this.status, newStatus);
        }
    }

    public void cancelByOperator(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new DomainValidationException("An operator cancellation requires a reason.");
        }
        if (this.status == ReservationStatus.CANCELLED) {
            return; // Already cancelled; keep the original reason.
        }
        if (this.status == ReservationStatus.COMPLETED) {
            throw new InvalidStatusTransitionException(this.status, ReservationStatus.CANCELLED);
        }

        this.cancellationReason = reason;
        this.status = ReservationStatus.CANCELLED;
    }

    public static Reservation book(User user, BikeInstance bikeInstance, RentalPeriod period, LocalDate currentDate, BigDecimal totalCost) {
        return new Reservation(user, bikeInstance, period, currentDate, totalCost);
    }

    private Reservation(User user, BikeInstance bikeInstance, RentalPeriod period, LocalDate currentDate, BigDecimal totalCost) {
        requireNonNull(currentDate, "Current Date");
        requireNonNull(period, "Rental period");
        this.user = requireNonNull(user, "User");
        this.bikeInstance = requireNonNull(bikeInstance, "Bike instance");
        this.totalCost = requireNonNull(totalCost, "Total cost");
        validateTotalCost(totalCost);

        if (!period.startDate().isAfter(currentDate)) {
            throw new DomainValidationException("Start date cannot be in past or present.");
        }

        this.startDate = period.startDate();
        this.endDate = period.endDate();
        this.status = ReservationStatus.PENDING;
    }

    public RentalPeriod getPeriod() {
        return new RentalPeriod(startDate, endDate);
    }

    private <T> T requireNonNull(T value, String fieldName) {
        if (value == null) {
            throw new DomainValidationException(fieldName + " is required.");
        }
        return value;
    }

    private void validateTotalCost(BigDecimal totalCost) {
        if (totalCost.compareTo(BigDecimal.ZERO) <= 0)
            throw new DomainValidationException("Total cost must be greater than zero.");
    }
}