package com.codearena.api.security;

import com.codearena.api.user.Role;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public record AuthUser(long id, String username, Role role) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public List<GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }
}
