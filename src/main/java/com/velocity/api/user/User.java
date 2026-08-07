package com.velocity.api.user;

import com.velocity.api.reservation.Reservation;
import com.velocity.api.common.City;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
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
    private City city;
    @Column(nullable = false, updatable = false)
    private LocalDate joinedDate;
    @OneToMany(mappedBy = "user")
    final private List<Reservation> reservations = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.joinedDate = LocalDate.now();
    }

    public void addReservation(Reservation reservation) {
        reservations.add(reservation);
    }

    // The intent-revealing factory method
    public static User registerClient(String email, String passwordHash, String fullName, String phone, City city) {
        return new User(email, passwordHash, fullName, phone, UserRole.CLIENT, city);
    }

    private User(String email, String passwordHash, String fullName, String phone, UserRole role, City city) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.phone = phone;
        this.role = role;
        this.city = city;
        this.status = UserStatus.ACTIVE;
    }
}
