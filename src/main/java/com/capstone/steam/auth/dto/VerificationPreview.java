package com.capstone.steam.auth.dto;

/** De qué cuenta es un enlace de verificación vigente (quien lo tiene ya es el destinatario del correo que la nombra). */
public record VerificationPreview(String username) {
}
