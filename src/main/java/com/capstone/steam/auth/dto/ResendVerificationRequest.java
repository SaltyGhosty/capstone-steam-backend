package com.capstone.steam.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResendVerificationRequest(@NotBlank @Email @Size(max = 254) String email) {

    /** Quita los espacios antes de validar: " Ana@Mail.com " es el mismo email que "ana@mail.com". */
    public ResendVerificationRequest {
        email = email == null ? null : email.trim();
    }
}
