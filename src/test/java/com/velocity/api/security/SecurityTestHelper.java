package com.velocity.api.security;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

public class SecurityTestHelper {
    public static RequestPostProcessor asUser(String userId, String role) {
        UserDetails dummyUser = User.builder()
                .username(userId)
                .password("password-not-needed")
                .roles(role)
                .build();

        Authentication authTicket = new UsernamePasswordAuthenticationToken(dummyUser, null, dummyUser.getAuthorities());
        return authentication(authTicket);
    }
}
