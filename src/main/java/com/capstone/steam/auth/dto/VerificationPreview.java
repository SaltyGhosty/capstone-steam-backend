package com.capstone.steam.auth.dto;

/** A quale account appartiene un link di verifica valido (chi lo possiede è già il destinatario dell'email che lo nomina). */
public record VerificationPreview(String username) {
}
