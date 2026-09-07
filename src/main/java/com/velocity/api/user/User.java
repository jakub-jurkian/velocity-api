package com.velocity.api.user;

import com.velocity.api.user.exception.InvalidUserStateException;
import com.velocity.api.common.City;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.regex.Pattern;


@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
public class User {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$");

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
    @CreatedDate
    private LocalDate joinedDate;
    @LastModifiedDate
    private Instant lastModified;
    @Version
    Long version;

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

    public void updateProfileByAdmin(String fullName, String phone, City city, String email) {
        validateFullName(fullName);
        validatePhone(phone);
        validateCity(city);
        validateEmail(email);
        this.fullName = fullName.trim();
        this.phone = phone;
        this.city = city;
        this.email = email;
    }

    public void changeRoleByAdmin(UserRole role) {
        validateRole(role);
        this.role = role;
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
        validateEmail(email);
        validatePasswordHash(passwordHash);
        validateFullName(fullName);
        validatePhone(phone);
        validateRole(role);
        validateCity(city);

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

    private void validateEmail(String email) {
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("Email must be properly formatted.");
        }
    }

    private void validatePasswordHash(String password) {
        if (password == null) {
            throw new IllegalArgumentException("Password is required");
        }
    }

    private void validateRole(UserRole role) {
        if (role == null) {
            throw new IllegalArgumentException("Role is required");
        }
    }
}
