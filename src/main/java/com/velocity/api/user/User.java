package com.velocity.api.user;

import com.velocity.api.common.City;
import com.velocity.api.common.exception.DomainValidationException;
import com.velocity.api.user.exception.InvalidUserStateException;
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
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+[1-9]\\d{7,14}$");

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
    private Long version;

    // The intent-revealing factory method
    public static User registerClient(String email, String passwordHash, String fullName, String phone, City city) {
        return new User(email, passwordHash, fullName, phone, UserRole.CLIENT, city);
    }

    public void updateProfile(String fullName, String phone, City city) {
        this.fullName = normalizeAndValidateFullName(fullName);
        this.phone = normalizeAndValidatePhone(phone);
        this.city = requireNonNull(city, "City");
    }

    public void updateProfileByAdmin(String fullName, String phone, City city, String email) {
        this.fullName = normalizeAndValidateFullName(fullName);
        this.phone = normalizeAndValidatePhone(phone);
        this.city = requireNonNull(city, "City");
        this.email = normalizeAndValidateEmail(email);
    }

    public void changeRoleByAdmin(UserRole role) {
        this.role = requireNonNull(role, "Role");
    }

    public void block() {
        if (this.status == UserStatus.BLOCKED) {
            throw new InvalidUserStateException("User is already blocked.");
        }
        this.status = UserStatus.BLOCKED;
    }

    public void unblock() {
        if (this.status == UserStatus.ACTIVE) {
            throw new InvalidUserStateException("User is already unblocked.");
        }
        this.status = UserStatus.ACTIVE;
    }

    private User(String email, String passwordHash, String fullName, String phone, UserRole role, City city) {
        validatePasswordHash(passwordHash);
        this.email = normalizeAndValidateEmail(email);
        this.fullName = normalizeAndValidateFullName(fullName);
        this.phone = normalizeAndValidatePhone(phone);
        this.passwordHash = passwordHash;
        this.role = requireNonNull(role, "Role");
        this.city = requireNonNull(city, "City");
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

    private String normalizeAndValidateEmail(String email) {
        if (email == null || email.isBlank()) throw new DomainValidationException("Email is required");
        String normalizedEmail = email.trim().toLowerCase();
        if (!EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
            throw new DomainValidationException("Email must be properly formatted.");
        }
        return normalizedEmail;
    }

    private void validatePasswordHash(String passwordHash) {
        if (passwordHash == null || passwordHash.isBlank()) throw new DomainValidationException("Password is required");

    }

    private <T> T requireNonNull(T value, String fieldName) {
        if (value == null) {
            throw new DomainValidationException(fieldName + " is required.");
        }
        return value;
    }
}
