package com.syncstream.auth;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class UserPrincipal implements UserDetails {
    private final UUID id;
    private final String email;
    private final String name;

    public UserPrincipal(UUID id, String email, String name) {
        this.id = id;
        this.email = email;
        this.name = name;
    }

    public UUID id() {
        return id;
    }

    public String email() {
        return email;
    }

    public String displayName() {
        return name;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        return id.toString();
    }
}
