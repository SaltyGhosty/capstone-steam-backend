package com.capstone.steam.auth.dto;

/** Resultado de registrarse: la sesión (verificación desactivada) o el aviso de que falta verificar el email. */
public sealed interface RegistrationResult permits AuthResponse, RegisterResponse {
}
