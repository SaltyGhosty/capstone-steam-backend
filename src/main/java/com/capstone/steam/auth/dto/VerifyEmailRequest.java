package com.capstone.steam.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Il link dell'email più la password con cui è stata fatta quella registrazione. */
public record VerifyEmailRequest(@NotBlank @Size(max = 200) String token, @NotBlank @Size(max = 100) String password) {
}
