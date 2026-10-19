package com.capstone.steam.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Cuerpo de {@code /api/auth/refresh} y {@code /api/auth/logout}. */
public record RefreshRequest(@NotBlank String refreshToken) {
}
