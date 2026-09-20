package com.velocity.api.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class JwtServiceTest {
    private JwtService jwtService;
    private static final String SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";


    @BeforeEach
    public void setUp() {
        jwtService = new JwtService(SECRET, 86400000L, Clock.systemUTC());
    }

    // prove generated token contains exact data we asked it to store
    @Test
    public void generateToken_validArguments_containsCorrectData() {
        // Arrange
        UserDetails userDetails = User.builder()
                .username("test@test.com")
                .password("hash")
                .build();
        // Act
        String generatedToken = jwtService.generateToken(userDetails);
        String username = jwtService.parseClaims(generatedToken).getSubject();

        // Assert
        assertThat(username).isEqualTo("test@test.com");
    }

    @Test
    public void extractUsername_expiredToken_throwsExpiredJwtException() {
        // Arrange
        JwtService expiringService = new JwtService(SECRET, -1000L, Clock.systemUTC());
        UserDetails userDetails = User.builder()
                .username("test@test.com")
                .password("hash")
                .build();
        String generatedToken = expiringService.generateToken(userDetails);
        // Act & Assert
        assertThrows(ExpiredJwtException.class, () -> expiringService.parseClaims(generatedToken).getSubject());
    }

    @Test
    public void extractUsername_signatureForgery_throwsSignatureException() {
        // Arrange
        UserDetails userDetails = User.builder()
                .username("test@test.com")
                .password("hash")
                .build();
        String generatedToken = jwtService.generateToken(userDetails);

        JwtService hackerService = new JwtService("303E635266556A586E3272357538782F413F4428472B4B6250645367566B5970", 86400000L, Clock.systemUTC());
        // Act & Assert
        assertThrows(SignatureException.class, () -> hackerService.parseClaims(generatedToken).getSubject());
    }

    @Test
    public void generateToken_always_assignsUniqueJti() {
        UserDetails user = User.builder().username("test@test.com").password("hash").build();

        String first = jwtService.parseClaims(jwtService.generateToken(user)).getId();
        String second = jwtService.parseClaims(jwtService.generateToken(user)).getId();

        assertThat(first).isNotBlank();
        assertThat(first).isNotEqualTo(second);
    }
}
