package com.capstone.steam.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank
        @Size(min = 3, max = 50)
        @Pattern(regexp = "^[A-Za-z0-9_.-]+$", message = "solo letras, números, '_', '.' y '-'")
        String username,

        @NotBlank @Email @Size(max = 255)
        String email,

        // BCrypt solo usa los primeros 72 bytes
        @NotBlank @Size(min = 8, max = 72)
        String password,

        @Size(max = 100)
        String displayName,

        /** Opcional; por defecto PLAYER. */
        AccountType accountType) {
}
