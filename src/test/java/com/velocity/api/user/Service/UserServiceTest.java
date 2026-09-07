package com.velocity.api.user.Service;

import com.velocity.api.auth.service.AuthService;
import com.velocity.api.common.City;
import com.velocity.api.common.exception.ResourceNotFoundException;
import com.velocity.api.user.User;
import com.velocity.api.auth.dto.UserProfileUpdateRequest;
import com.velocity.api.user.repository.UserRepository;
import com.velocity.api.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;
    @InjectMocks
    private AuthService authService;

    @DisplayName("Given a valid session but missing user, getProfile should throw exception")
    @Test
    public void getProfile_userDeleted_throwsException() {
        // Arrange: Create mocks for the security context
        SecurityContext securityContext = mock(SecurityContext.class);
//        Authentication authentication = mock(Authentication.class);
//        UserDetails userDetails = mock(UserDetails.class);

        // Configure the mocks to return a specific email
        String testEmail = "test@test.com";
//        when(userDetails.getUsername()).thenReturn(testEmail);
//        when(authentication.getPrincipal()).thenReturn(userDetails);
//        when(securityContext.getAuthentication()).thenReturn(authentication);

        // Inject the mocked context into the static Spring Security holder
        SecurityContextHolder.setContext(securityContext);

        // Configure the repository to return an empty box
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.empty());

        // Act & Assert: Prove that the exception is thrown and halts execution
        assertThatThrownBy(() -> authService.getProfile(testEmail))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(testEmail);

        // Cleanup: Clear the context so it does not pollute other tests
        SecurityContextHolder.clearContext();
    }

    @Test
    public void updateProfile_fieldOmitted_retainsExistingValue() {
        User user = createStandardClient();
        when(userRepository.findById(any())).thenReturn(Optional.of(user));

        // Client omits all fields from the JSON payload
        UserProfileUpdateRequest request = new UserProfileUpdateRequest(
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined()
        );

        userService.updateProfile(user.getId(), request);

        assertThat(user.getFullName()).isEqualTo("Test");
        assertThat(user.getPhone()).isEqualTo("+48000000000");
        assertThat(user.getCity()).isEqualByComparingTo(City.GDANSK);
    }

    @Test
    public void updateProfile_fieldExplicitlyNull_throwsIllegalArgumentException() {
        User user = createStandardClient();
        when(userRepository.findById(any())).thenReturn(Optional.of(user));

        // Client explicitly sends {"phone": null}
        UserProfileUpdateRequest request = new UserProfileUpdateRequest(
                JsonNullable.undefined(),
                JsonNullable.of(null),
                JsonNullable.undefined()
        );

        assertThatThrownBy(() -> userService.updateProfile(user.getId(), request)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Phone number is required");
    }

    @Test
    public void updateProfile_validNewData_updatesFields() {
        User user = createStandardClient();
        when(userRepository.findById(any())).thenReturn(Optional.of(user));

        // Client sends new valid data for all fields
        UserProfileUpdateRequest request = new UserProfileUpdateRequest(
                JsonNullable.of("New Name"),
                JsonNullable.of("+48999999999"),
                JsonNullable.of(City.WARSAW)
        );

        userService.updateProfile(user.getId(), request);

        assertThat(user.getFullName()).isEqualTo("New Name");
        assertThat(user.getPhone()).isEqualTo("+48999999999");
        assertThat(user.getCity()).isEqualByComparingTo(City.WARSAW);
    }

    @Test
    public void updateProfile_userNotFound_throwsResourceNotFoundException() {
        // Simulating the database returning nothing for a rogue UUID
        when(userRepository.findById(any())).thenReturn(Optional.empty());

        UserProfileUpdateRequest request = new UserProfileUpdateRequest(
                JsonNullable.of("New Name"),
                JsonNullable.undefined(),
                JsonNullable.undefined()
        );

        assertThatThrownBy(() -> userService.updateProfile(UUID.randomUUID(), request)).isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found");
    }

    private User createStandardClient() {
        return User.registerClient("test@test.com", "hashed", "Test", "+48000000000", City.GDANSK);
    }
}
