package com.velocity.api.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {
    private final SecretKey signInKey;
    private final JwtParser parser;
    private final long jwtExpiration;
    private final Clock clock;

    public JwtService(
            @Value("${security.jwt.secret-key}") String secretKey,
            @Value("${security.jwt.expiration-ms}") long jwtExpiration,
            Clock clock
    ) {
        this.signInKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretKey));
        this.parser = Jwts.parser().verifyWith(this.signInKey).build();
        this.jwtExpiration = jwtExpiration;
        this.clock = clock;
    }

    // Verifies signature and expiry once - every accessor reads from the result
    public Claims parseClaims(String token) {
        return parser.parseSignedClaims(token).getPayload();
    }

    public String generateToken(UserDetails userDetails) {
        Instant now = Instant.now(clock);
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userDetails.getUsername())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(jwtExpiration)))
                .signWith(signInKey)
                .compact();
    }
}
