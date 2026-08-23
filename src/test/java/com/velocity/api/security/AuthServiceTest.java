package com.velocity.api.security;

import com.velocity.api.common.City;
import com.velocity.api.security.repository.TokenBlacklistRepository;
import com.velocity.api.user.User;
import com.velocity.api.user.dto.UserLoginRequest;
import com.velocity.api.user.dto.UserLoginResponse;
import com.velocity.api.user.repository.UserRepository;
import com.velocity.api.user.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private UserRepository userRepository;
    @Mock
    private JwtService jwtService;
    @Mock
    private TokenBlacklistRepository tokenBlacklistRepository;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    public void setUp() {
        ReflectionTestUtils.setField(authService, "jwtExpiration", 86400000L);
    }

    @Test
    public void login_validData_orchestratesDependenciesCorrectly() {
        // Arrange
        // Create a dummy Spring Security UserDetails (like in JwtServiceTest)
        UserDetails dummyUserDetails = org.springframework.security.core.userdetails.User.builder()
                .username("test@test.com")
                .password("hash")
                .build();
        // Create a mock Authentication object
        Authentication mockAuth = mock(Authentication.class);

        // Tell the mock what to return
        when(mockAuth.getPrincipal()).thenReturn(dummyUserDetails);

        // Tell the AuthenticationManager to return your mockAuth!
        when(authenticationManager.authenticate(any())).thenReturn(mockAuth);
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(User.registerClient("test@test.com", "hash", "Test", "+48000400000", City.GDANSK)));
        when(jwtService.generateToken(any(), any())).thenReturn("fake-jwt-string");
        UserLoginRequest request = new UserLoginRequest("test@test.com", "hash");
        // Act
        UserLoginResponse userLoginResponse = authService.login(request);
        // Assert
        assertThat(userLoginResponse).hasFieldOrPropertyWithValue("accessToken", "fake-jwt-string");
        assertThat(userLoginResponse).hasFieldOrPropertyWithValue("expiresIn", 86400000L);
    }

    @Test
    public void login_invalidCredentials_throwsExceptionAndAborts() {
        // Arrange
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Bad Credentials"));
        UserLoginRequest request = new UserLoginRequest("test@test.com", "hash");
        // Act & Assert
        assertThrows(BadCredentialsException.class, () -> authService.login(request));

        // Mockito's verify to assert zero interactions - database was never queried
        verify(userRepository, never()).findByEmail(anyString());
        verify(jwtService, never()).generateToken(any(), any());
    }

    @Test
    public void logout_validToken_savesToBlacklist() {
        // Arrange
        when(jwtService.extractExpiration(anyString())).thenReturn(Instant.now().plusSeconds(3600));
        authService.logout("fake-token-123");
        ArgumentCaptor<Duration> captor = ArgumentCaptor.forClass(Duration.class);
        verify(tokenBlacklistRepository).blacklist(eq("fake-token-123"), captor.capture());
        Duration capturedTtl = captor.getValue();
        assertThat(capturedTtl).isBetween(Duration.ofSeconds(3590), Duration.ofSeconds(3600));
    }

    @Test
    public void logout_expiredToken_doesNotCallRepository() {
        // Arrange
        when(jwtService.extractExpiration(anyString())).thenReturn(Instant.now().minusMillis(3600));
        // Act
        authService.logout("fake-token-123");
        // Assert
        verifyNoInteractions(tokenBlacklistRepository);
    }
}
