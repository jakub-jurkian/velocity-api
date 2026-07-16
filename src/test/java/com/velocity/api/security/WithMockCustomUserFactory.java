package com.velocity.api.security;

import lombok.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

public class WithMockCustomUserFactory implements WithSecurityContextFactory<WithMockCustomUser> {

    @Override
    @NonNull
    public SecurityContext createSecurityContext(@NonNull WithMockCustomUser annotation) {
        // Extract the data from the annotation
        String userId = annotation.userId();
        String role = annotation.role();
        // Build the UserDetails profile
        UserDetails dummyUser = User.builder()
                .username(userId)
                .password("password-not-needed")
                .roles(role)
                .build();
        // Print the Auth ticket
        Authentication authTicket = new UsernamePasswordAuthenticationToken(dummyUser, null, dummyUser.getAuthorities());
        // Create an empty SecurityContext and put ticket inside

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authTicket);

        return context;
    }
}
