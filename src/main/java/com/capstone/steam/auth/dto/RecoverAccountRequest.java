package com.capstone.steam.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Cuerpo de {@code /api/auth/recover-account}; {@code login} acepta username o email. */
public record RecoverAccountRequest(@NotBlank String login, @NotBlank String password) {
}
