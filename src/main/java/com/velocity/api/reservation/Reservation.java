package com.velocity.api.reservation;

import com.velocity.api.bike.BikeInstance;
import com.velocity.api.common.exception.InvalidStatusTransitionException;
import com.velocity.api.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "reservations")
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
    @Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    private ReservationStatus status = ReservationStatus.PENDING;
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
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
        this.createdAt = LocalDateTime.now();
    }

    public void transitionTo(ReservationStatus newStatus) {
        if (this.status == newStatus) return;

        boolean isValid = switch (this.status) {
            case PENDING -> newStatus == ReservationStatus.CONFIRMED || newStatus == ReservationStatus.CANCELLED;
            case CONFIRMED -> newStatus == ReservationStatus.COMPLETED || newStatus == ReservationStatus.CANCELLED;
            case CANCELLED, COMPLETED -> false;
        };

        if (!isValid) {
            throw new InvalidStatusTransitionException(this.status, newStatus);
        }

        this.status = newStatus;
    }

    public void setStatusForTest(ReservationStatus newStatus) {
        this.status = newStatus;
    }
}
