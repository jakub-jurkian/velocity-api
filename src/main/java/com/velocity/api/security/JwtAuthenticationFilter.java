package com.velocity.api.security;

import com.velocity.api.security.repository.TokenBlacklistRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.util.Optional;

@Component
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final TokenBlacklistRepository tokenBlacklistRepository;
    private final HandlerExceptionResolver resolver;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            UserDetailsService userDetailsService,
            TokenBlacklistRepository tokenBlacklistRepository,
            // not @RequiredArgsConstructor cause of qualifier below
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver
    ) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.tokenBlacklistRepository = tokenBlacklistRepository;
        this.resolver = resolver;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        Optional<String> rawTokenOptional = extractBearerToken(request);
        if (rawTokenOptional.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        String rawToken = rawTokenOptional.get();

        try {
            if (tokenBlacklistRepository.isBlacklisted(rawToken)) {
                log.warn("JWT token is blacklisted.");
                resolver.resolveException(request, response, null, new BadCredentialsException("Token is revoked."));
                return;
            }

            authenticateToken(rawToken, request);

        } catch (AuthenticationException e) {
            log.warn("Authentication failed: {}", e.getMessage());
            resolver.resolveException(request, response, null, e);
            return;
        } catch (JwtException e) {
            log.warn("JWT parsing failed: {}", e.getMessage());
            resolver.resolveException(request, response, null, new BadCredentialsException("Invalid or expired token", e));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private Optional<String> extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }
        return Optional.of(header.substring(BEARER_PREFIX.length()));
    }

    private void authenticateToken(String rawToken, HttpServletRequest request) {
        String username = jwtService.extractUsername(rawToken);
        if (username == null || username.isBlank()) {
            throw new BadCredentialsException("Token subject is missing.");
        }

        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            return;
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(username);

        if (!userDetails.isAccountNonLocked()) {
            throw new LockedException("Account is blocked.");
        }

        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                userDetails.getAuthorities()
        );
        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authToken);
    }
}