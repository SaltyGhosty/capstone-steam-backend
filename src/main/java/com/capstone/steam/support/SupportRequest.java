package com.capstone.steam.support;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param email obligatorio sin sesión; con sesión se ignora y se usa el de la cuenta
 */
public record SupportRequest(
        @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 3, max = 150) String subject,
        @NotBlank @Size(min = 10, max = 3000) String message) {

    /** Quita los espacios de los bordes antes de validar; un email vacío cuenta como ausente. */
    public SupportRequest {
        email = email == null || email.isBlank() ? null : email.strip();
        subject = subject == null ? null : subject.strip();
        message = message == null ? null : message.strip();
    }
}
