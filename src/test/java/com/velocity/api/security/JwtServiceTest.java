package com.velocity.api.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class JwtServiceTest {
    private JwtService jwtService;

    @BeforeEach
    public void setUp() {
        jwtService = new JwtService();

        ReflectionTestUtils.setField(jwtService, "secretKey", "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970");
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 86400000L);
    }

    // prove generated token contains exact data we asked it to store
    @Test
    public void generateToken_validArguments_containsCorrectData() {
        // Arrange
        UserDetails userDetails = User.builder()
                .username("test@test.com")
                .password("hash")
                .build();
        HashMap<String, Object> extraClaims = new HashMap<>();
        String randomUUIDString = UUID.randomUUID().toString();
        extraClaims.put("id", randomUUIDString);
        extraClaims.put("role", "CLIENT");
        // Act
        String generatedToken = jwtService.generateToken(extraClaims, userDetails);
        String username = jwtService.extractUsername(generatedToken);
        String extractedId = jwtService.extractClaim(generatedToken, "id", String.class);
        String extractedRole = jwtService.extractClaim(generatedToken, "role", String.class);

        // Assert
        assertThat(username).isEqualTo("test@test.com");
        assertThat(extractedId).isEqualTo(randomUUIDString);
        assertThat(extractedRole).isEqualTo("CLIENT");
    }

    @Test
    public void generateToken_expiredToken_throwsExpiredJwtException() {
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", -1000L);

        // Arrange
        UserDetails userDetails = User.builder()
                .username("test@test.com")
                .password("hash")
                .build();
        HashMap<String, Object> extraClaims = new HashMap<>();
        String randomUUIDString = UUID.randomUUID().toString();
        extraClaims.put("id", randomUUIDString);
        extraClaims.put("role", "CLIENT");
        String generatedToken = jwtService.generateToken(extraClaims, userDetails);
        // Act & Assert
        assertThrows(ExpiredJwtException.class, () -> {
            jwtService.extractUsername(generatedToken);
        });
    }

    @Test
    public void generateToken_signatureForgery_throwsSignatureException() {
        // Arrange
        UserDetails userDetails = User.builder()
                .username("test@test.com")
                .password("hash")
                .build();
        HashMap<String, Object> extraClaims = new HashMap<>();
        String randomUUIDString = UUID.randomUUID().toString();
        extraClaims.put("id", randomUUIDString);
        extraClaims.put("role", "CLIENT");
        String generatedToken = jwtService.generateToken(extraClaims, userDetails);

        JwtService hackerService = new JwtService();
        ReflectionTestUtils.setField(hackerService, "secretKey", "303E635266556A586E3272357538782F413F4428472B4B6250645367566B5970");
        // Act & Assert
        assertThrows(SignatureException.class, () -> {
            hackerService.extractUsername(generatedToken);
        });
    }
}
