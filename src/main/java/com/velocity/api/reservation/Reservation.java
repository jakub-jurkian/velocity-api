package com.velocity.api.reservation;

import com.velocity.api.bike.BikeInstance;
import com.velocity.api.reservation.exception.InvalidStatusTransitionException;
import com.velocity.api.reservation.exception.LateCancelException;
import com.velocity.api.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "reservations")
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
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bike_instance_id", nullable = false)
    private BikeInstance bikeInstance;

    @Version // enables optimistic locking
    private Long version;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    public void transitionTo(ReservationStatus newStatus, LocalDate currentDate) throws LateCancelException {
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
            throw new LateCancelException("The reservation cannot be cancelled after end date.");
        }

        this.status = newStatus;
    }

    public static Reservation book(User user, BikeInstance bikeInstance, LocalDate startDate, LocalDate endDate, BigDecimal totalCost) {
        return new Reservation(user, bikeInstance, startDate, endDate, totalCost);
    }

    private Reservation(User user, BikeInstance bikeInstance, LocalDate startDate, LocalDate endDate, BigDecimal totalCost) {
        long days = ChronoUnit.DAYS.between(startDate, endDate);
        if (days < 3 || days > 21) {
            throw new IllegalArgumentException("Amount of days cannot be greater than 21 or smaller than 3.");
        }
        if (!endDate.isAfter(startDate)) {
            throw new IllegalArgumentException("End date must be greater than Start date.");
        }
        this.user = user;
        this.bikeInstance = bikeInstance;
        this.startDate = startDate;
        this.endDate = endDate;
        this.totalCost = totalCost;
        this.status = ReservationStatus.PENDING;
    }
}