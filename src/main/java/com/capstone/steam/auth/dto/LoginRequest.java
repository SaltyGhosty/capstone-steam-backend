package com.capstone.steam.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** {@code login} acepta username o email. */
public record LoginRequest(@NotBlank String login, @NotBlank String password) {
}
