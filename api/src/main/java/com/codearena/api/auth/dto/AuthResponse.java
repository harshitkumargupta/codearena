package com.codearena.api.auth.dto;

public record AuthResponse(String accessToken, String tokenType, long expiresInSeconds, UserResponse user) {
}
