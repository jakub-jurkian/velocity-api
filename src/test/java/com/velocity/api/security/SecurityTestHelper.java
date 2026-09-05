package com.velocity.api.security;

import com.velocity.api.common.City;
import com.velocity.api.user.UserStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

public class SecurityTestHelper {
    public static RequestPostProcessor asUser(String userId, String role) {
        UserDetails dummyUser = new CustomUserDetails(
                UUID.fromString(userId),
                "test@test.com",
                "password-not-needed",
                City.GDANSK,
                UserStatus.ACTIVE,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );

        Authentication authTicket = new UsernamePasswordAuthenticationToken(dummyUser, null, dummyUser.getAuthorities());
        return authentication(authTicket);
    }
}
