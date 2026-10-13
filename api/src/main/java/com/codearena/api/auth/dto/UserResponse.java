package com.codearena.api.auth.dto;

import com.codearena.api.user.Role;
import com.codearena.api.user.User;
import java.time.Instant;

public record UserResponse(long id, String username, String email, Role role, Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getRole(), user.getCreatedAt());
    }
}
