package com.capstone.steam.notification.event;

/**
 * Se publica dentro de la transacción de registro; el listener de correo (bloque 7)
 * lo consume en AFTER_COMMIT. Solo datos primitivos, nunca entidades.
 *
 * @param locale idioma del correo (es, en, it), leído de {@code User} dentro de la transacción
 */
public record UserRegisteredEvent(Long userId, String email, String displayName, String locale) {
}
