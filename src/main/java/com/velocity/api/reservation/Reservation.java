package com.velocity.api.reservation;

import com.velocity.api.bike.BikeInstance;
import com.velocity.api.common.exception.DomainValidationException;
import com.velocity.api.reservation.exception.InvalidStatusTransitionException;
import com.velocity.api.reservation.exception.LateCancelException;
import com.velocity.api.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "reservations")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Reservation {
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

    @Version // enables optimistic locking
    private Long version;

    public void transitionTo(ReservationStatus newStatus, LocalDate currentDate) {
        if (this.status == newStatus) return;

        boolean isValid = switch (this.status) {
            case PENDING -> newStatus == ReservationStatus.CONFIRMED || newStatus == ReservationStatus.CANCELLED;
            case CONFIRMED -> newStatus == ReservationStatus.COMPLETED || newStatus == ReservationStatus.CANCELLED;
            case CANCELLED, COMPLETED -> false;
        };

        if (!isValid) {
            throw new InvalidStatusTransitionException(this.status, newStatus);
        }

        if (newStatus.equals(ReservationStatus.CANCELLED) && !currentDate.isBefore(this.startDate)) {
            throw new LateCancelException("The reservation cannot be cancelled on or after the start date.");
        }

        this.status = newStatus;
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

    public static Reservation book(User user, BikeInstance bikeInstance, LocalDate startDate, LocalDate endDate, LocalDate currentDate, BigDecimal totalCost) {
        return new Reservation(user, bikeInstance, startDate, endDate, currentDate, totalCost);
    }

    private Reservation(User user, BikeInstance bikeInstance, LocalDate startDate, LocalDate endDate, LocalDate currentDate, BigDecimal totalCost) {
        requireNonNull(currentDate, "Current Date");
        this.user = requireNonNull(user, "User");
        this.bikeInstance = requireNonNull(bikeInstance, "Bike instance");
        this.startDate = requireNonNull(startDate, "Start date");
        this.endDate = requireNonNull(endDate, "End date");
        this.totalCost = requireNonNull(totalCost, "Total cost");
        this.status = ReservationStatus.PENDING;
        validateTotalCost(totalCost);

        if (!startDate.isAfter(currentDate)) {
            throw new DomainValidationException("Start date cannot be in past or present.");
        }

        long days = ChronoUnit.DAYS.between(startDate, endDate);
        if (days < 3 || days > 21) {
            throw new DomainValidationException("Amount of days must be between 3 and 21.");
        }
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