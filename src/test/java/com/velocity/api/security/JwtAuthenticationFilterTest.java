package com.velocity.api.security;


import com.velocity.api.security.repository.TokenBlacklistRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import com.velocity.api.security.exception.InvalidTokenException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class JwtAuthenticationFilterTest {
    @Mock
    private TokenBlacklistRepository tokenBlacklistRepository;
    @Mock
    private UserDetailsService userDetailsService;
    @Mock
    private HandlerExceptionResolver resolver;
    @Mock
    private JwtService jwtService;

    @InjectMocks
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    public void jwtAuthenticationFilterChain_blacklistedToken_requestRejected() throws ServletException, IOException {
        Claims claims = Jwts.claims()
                .id("jti-123")
                .subject("test@test.com")
                .build();
        when(jwtService.parseClaims("fake-token-123")).thenReturn(claims);
        when(tokenBlacklistRepository.isBlacklisted("jti-123")).thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer fake-token-123");

        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain filterChain = mock(FilterChain.class);

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // proves doFilter was called
        verifyNoInteractions(filterChain);
        verifyNoInteractions(userDetailsService);
        verify(resolver).resolveException(
                eq(request),
                eq(response),
                isNull(),
                // A revoked token is a token problem, not a password problem:
                // BadCredentialsException here made the API tell the user
                // their email or password was wrong.
                any(InvalidTokenException.class)
        );
        verify(tokenBlacklistRepository).isBlacklisted("jti-123");
    }
}
