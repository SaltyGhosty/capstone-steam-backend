package com.capstone.steam.support;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param email obbligatoria senza sessione; con sessione si ignora e si usa quella dell'account
 */
public record SupportRequest(
        @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 3, max = 150) String subject,
        @NotBlank @Size(min = 10, max = 3000) String message) {

    /** Toglie gli spazi ai bordi prima di validare; un'email vuota conta come assente. */
    public SupportRequest {
        email = email == null || email.isBlank() ? null : email.strip();
        subject = subject == null ? null : subject.strip();
        message = message == null ? null : message.strip();
    }
}
