package com.capstone.steam.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank
        @Size(min = 3, max = 50)
        @Pattern(regexp = "^[A-Za-z0-9_.-]+$", message = "{validation.username.pattern}")
        // reservados para las cuentas anonimizadas (deleted_<id>): ocuparlos antes haría fallar la baja por UNIQUE
        @Pattern(regexp = "^(?!deleted_).*$", flags = Pattern.Flag.CASE_INSENSITIVE, message = "{validation.reserved}")
        String username,

        @NotBlank @Email @Size(max = 255)
        @Pattern(regexp = "^(?!.*@deleted\\.invalid\\s*$).*$", flags = Pattern.Flag.CASE_INSENSITIVE,
                message = "{validation.reserved}")
        String email,

        // BCrypt solo usa los primeros 72 bytes
        @NotBlank @Size(min = 8, max = 72)
        String password,

        @Size(max = 100)
        String displayName,

        /** Opcional; por defecto PLAYER. */
        AccountType accountType,

        /** Opcional; idioma de los correos. Null = "es". */
        @Pattern(regexp = "^(es|en|it)$", message = "{validation.locale}")
        String locale) {
}
