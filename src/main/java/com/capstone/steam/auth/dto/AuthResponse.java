package com.capstone.steam.auth.dto;

import com.capstone.steam.user.dto.UserResponse;

public record AuthResponse(String accessToken, String tokenType, long expiresIn, String refreshToken,
        UserResponse user) implements RegistrationResult {

    public static AuthResponse bearer(String token, long expiresInSeconds, String refreshToken, UserResponse user) {
        return new AuthResponse(token, "Bearer", expiresInSeconds, refreshToken, user);
    }
}
