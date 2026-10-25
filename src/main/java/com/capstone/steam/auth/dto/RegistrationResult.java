package com.capstone.steam.auth.dto;

/** Risultato della registrazione: la sessione (verifica disattivata) o l'avviso che manca la verifica dell'email. */
public sealed interface RegistrationResult permits AuthResponse, RegisterResponse {
}
