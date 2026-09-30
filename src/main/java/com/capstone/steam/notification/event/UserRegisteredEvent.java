package com.capstone.steam.notification.event;

/**
 * Se publica dentro de la transacción de registro; el listener de correo (bloque 7)
 * lo consume en AFTER_COMMIT. Solo datos primitivos, nunca entidades.
 */
public record UserRegisteredEvent(Long userId, String email, String displayName) {
}
