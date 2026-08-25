package com.velocity.api.user;

import com.velocity.api.common.exception.InvalidUserStateException;
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

    public void updateProfile(String fullName, String phone, City city) {
        validateFullName(fullName);
        validatePhone(phone);
        validateCity(city);
        this.fullName = fullName.trim();
        this.phone = phone;
        this.city = city;
    }

    public void block() {
        if (this.status == UserStatus.DELETED) {
            throw new InvalidUserStateException("Cannot modify a deleted user.");
        }
        if (this.status == UserStatus.ACTIVE) {
            this.status = UserStatus.BLOCKED;
        }
    }

    public void unblock() {
        if (this.status == UserStatus.DELETED) {
            throw new InvalidUserStateException("Cannot modify a deleted user.");
        }
        if (this.status == UserStatus.BLOCKED) {
            this.status = UserStatus.ACTIVE;
        }
    }

    public void softDelete() {
        if (this.status != UserStatus.DELETED) {
            this.status = UserStatus.DELETED;
        }
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

    private void validateFullName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("Full name is required");
        }
        if (fullName.length() < 2 || fullName.length() > 100) {
            throw new IllegalArgumentException("Name must be between 2 and 100 characters");
        }
    }

    private void validatePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            throw new IllegalArgumentException("Phone number is required");
        }
        if (!phone.matches("^\\+?[1-9]\\d{1,14}$")) {
            throw new IllegalArgumentException("Phone must be a valid international format (e.g., +48123456789)");
        }
    }

    private void validateCity(City city) {
        if (city == null) {
            throw new IllegalArgumentException("City is required");
        }
    }
}
