package com.capstone.steam.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank
        @Size(min = 3, max = 50)
        @Pattern(regexp = "^[A-Za-z0-9_.-]+$", message = "{validation.username.pattern}")
        // riservati agli account anonimizzati (deleted_<id>): occuparli farebbe fallire la cancellazione per UNIQUE
        @Pattern(regexp = "^(?!deleted_).*$", flags = Pattern.Flag.CASE_INSENSITIVE, message = "{validation.reserved}")
        String username,

        @NotBlank @Email @Size(max = 255)
        @Pattern(regexp = "^(?!.*@deleted\\.invalid\\s*$).*$", flags = Pattern.Flag.CASE_INSENSITIVE,
                message = "{validation.reserved}")
        String email,

        // BCrypt usa solo i primi 72 byte
        @NotBlank @Size(min = 8, max = 72)
        String password,

        @Size(max = 100)
        String displayName,

        /** Opzionale; di default PLAYER. */
        AccountType accountType,

        /** Opzionale; lingua delle email. Null = "es". */
        @Pattern(regexp = "^(es|en|it)$", message = "{validation.locale}")
        String locale) {
}
