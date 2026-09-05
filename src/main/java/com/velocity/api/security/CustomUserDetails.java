package com.velocity.api.security;

import com.velocity.api.common.City;
import com.velocity.api.user.UserStatus;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.UUID;

public record CustomUserDetails(
        UUID id,
        String username,
        String password,
        City city,
        UserStatus status,
        Collection<? extends GrantedAuthority> authorities) implements UserDetails {

    @Override
    public @NullMarked Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    public UUID getId() {
        return this.id;
    }

    public City getCity() {
        return this.city;
    }

    @Override
    public @Nullable String getPassword() {
        return password;
    }

    @Override
    public @NullMarked String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonLocked() {
        return this.status != UserStatus.BLOCKED;
    }
}
