package com.velocity.velocity.api.user;

import com.velocity.velocity.api.reservation.Reservation;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false, unique = true)
    private String email;
    @Column(nullable = false)
    private String passwordHash;
    @Column(nullable = false)
    private String fullName;
    @Column(nullable = false, unique = true)
    private String phone;
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private UserStatus status;
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private UserRole role;
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private UserCity city;
    @Column(nullable = false, updatable = false)
    private LocalDateTime joinedDate;
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    List<Reservation> reservations = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.joinedDate = LocalDateTime.now();
    }

    public void addReservation(Reservation reservation) {
        reservations.add(reservation);
        reservation.setUser(this); // Syncing the other side
    }
}
