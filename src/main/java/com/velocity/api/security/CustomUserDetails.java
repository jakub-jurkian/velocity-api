package com.velocity.api.security;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public record CustomUserDetails(
        UUID id,
        String username,
        String password,
        Collection<? extends GrantedAuthority> authorities) implements UserDetails {

    @Override
    public @NullMarked Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    public UUID getId() {
        return this.id;
    }

    @Override
    public @Nullable String getPassword() {
        return password;
    }

    @Override
    public @NullMarked String getUsername() {
        return username;
    }

}
