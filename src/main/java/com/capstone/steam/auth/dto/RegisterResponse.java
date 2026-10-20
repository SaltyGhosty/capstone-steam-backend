package com.capstone.steam.auth.dto;

/** Cuenta creada pero sin sesión: hay que pulsar el enlace del correo. El {@code id} lo usan el seed y Postman. */
public record RegisterResponse(Long id, String email, boolean verificationRequired) implements RegistrationResult {
}
