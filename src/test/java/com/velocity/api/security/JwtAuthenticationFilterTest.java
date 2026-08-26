package com.velocity.api.security;


import com.velocity.api.security.repository.TokenBlacklistRepository;
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
import org.springframework.security.core.userdetails.UserDetailsService;

import java.io.IOException;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class JwtAuthenticationFilterTest {
    @Mock
    private TokenBlacklistRepository tokenBlacklistRepository;
    @Mock
    private UserDetailsService userDetailsService;

    @InjectMocks
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    public void jwtAuthenticationFilterChain_blacklistedToken_requestRejected() throws ServletException, IOException {
        when(tokenBlacklistRepository.isBlacklisted(anyString())).thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer fake-token-123");

        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain filterChain = mock(FilterChain.class);

        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // proves doFilter was called
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(userDetailsService);
    }
}
