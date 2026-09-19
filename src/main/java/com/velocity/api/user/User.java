package com.velocity.api.user;

import com.velocity.api.common.City;
import com.velocity.api.common.exception.DomainValidationException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;


@Entity
@Table(name = "users")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA
@Getter
public class User {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[1-9]\\d{1,14}$");

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
    private Instant createdAt;
    @LastModifiedDate
    private Instant lastModified;
    @Version
    Long version;

    // The intent-revealing factory method
    public static User registerClient(String email, String passwordHash, String fullName, String phone, City city) {
        return new User(email, passwordHash, fullName, phone, UserRole.CLIENT, city);
    }

    public void updateProfile(String fullName, String phone, City city) {
        validateCity(city);
        this.fullName = normalizeAndValidateFullName(fullName);
        this.phone = normalizeAndValidatePhone(phone);
        this.city = city;
    }

    public void updateProfileByAdmin(String fullName, String phone, City city, String email) {
        validateCity(city);
        this.fullName = normalizeAndValidateFullName(fullName);
        this.phone = normalizeAndValidatePhone(phone);
        this.city = city;
        this.email = normalizeAndValidateEmail(email);
    }

    public void changeRoleByAdmin(UserRole role) {
        validateRole(role);
        this.role = role;
    }

    public void block() {
        if (this.status == UserStatus.ACTIVE) this.status = UserStatus.BLOCKED;
    }

    public void unblock() {
        if (this.status == UserStatus.BLOCKED) this.status = UserStatus.ACTIVE;
    }

    private User(String email, String passwordHash, String fullName, String phone, UserRole role, City city) {
        validatePasswordHash(passwordHash);
        validateRole(role);
        validateCity(city);

        this.email = normalizeAndValidateEmail(email);
        this.fullName = normalizeAndValidateFullName(fullName);
        this.phone = normalizeAndValidatePhone(phone);
        this.passwordHash = passwordHash;
        this.role = role;
        this.city = city;
        this.status = UserStatus.ACTIVE;
    }

    private String normalizeAndValidateFullName(String fullName) {
        if (fullName == null || fullName.isBlank()) throw new DomainValidationException("Full name is required");
        String normalizedFullName = fullName.trim();
        if (normalizedFullName.length() < 2 || normalizedFullName.length() > 100) {
            throw new DomainValidationException("Name must be between 2 and 100 characters");
        }
        return normalizedFullName;
    }

    private String normalizeAndValidatePhone(String phone) {
        if (phone == null || phone.isBlank()) throw new DomainValidationException("Phone number is required");
        String normalizedPhone = phone.trim();
        if (!PHONE_PATTERN.matcher(normalizedPhone).matches()) {
            throw new DomainValidationException("Phone must be a valid international format (e.g., +48123456789)");
        }
        return normalizedPhone;
    }

    private void validateCity(City city) {
        if (city == null) throw new DomainValidationException("City is required");
    }

    private String normalizeAndValidateEmail(String email) {
        if (email == null || email.isBlank()) throw new DomainValidationException("Email is required");
        String normalizedEmail = email.trim().toLowerCase();
        if (!EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
            throw new DomainValidationException("Email must be properly formatted.");
        }
        return normalizedEmail;
    }

    private void validatePasswordHash(String password) {
        if (password == null) throw new DomainValidationException("Password is required");
    }

    private void validateRole(UserRole role) {
        if (role == null) throw new DomainValidationException("Role is required");
    }
}
