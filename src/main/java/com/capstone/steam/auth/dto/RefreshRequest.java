package com.capstone.steam.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cuerpo de {@code /api/auth/refresh} y {@code /api/auth/logout}. */
public record RefreshRequest(@NotBlank @Size(max = 128) String refreshToken) {
}
