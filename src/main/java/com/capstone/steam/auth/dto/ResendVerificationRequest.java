package com.capstone.steam.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResendVerificationRequest(@NotBlank @Email @Size(max = 254) String email) {

    /** Toglie gli spazi prima di validare: " Ana@Mail.com " è la stessa email di "ana@mail.com". */
    public ResendVerificationRequest {
        email = email == null ? null : email.trim();
    }
}
