package com.capstone.steam.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corpo di {@code /api/auth/refresh} e {@code /api/auth/logout}. */
public record RefreshRequest(@NotBlank @Size(max = 128) String refreshToken) {
}
