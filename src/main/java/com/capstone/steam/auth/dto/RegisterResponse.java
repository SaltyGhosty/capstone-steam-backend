package com.capstone.steam.auth.dto;

/** Account creato ma senza sessione: bisogna cliccare il link dell'email. L'{@code id} lo usano il seed e Postman. */
public record RegisterResponse(Long id, String email, boolean verificationRequired) implements RegistrationResult {
}
