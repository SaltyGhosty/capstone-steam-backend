package com.capstone.steam.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Corpo di {@code /api/auth/recover-account}; {@code login} accetta username o email. */
public record RecoverAccountRequest(@NotBlank String login, @NotBlank String password) {
}
