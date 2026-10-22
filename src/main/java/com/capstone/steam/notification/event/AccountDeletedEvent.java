package com.capstone.steam.notification.event;

/**
 * Se publica dentro de la transacción que anonimiza la cuenta, con los datos que tenía ANTES de anonimizar
 * (después el email ya no existe). El correo sale en AFTER_COMMIT. Solo datos primitivos, nunca entidades.
 */
public record AccountDeletedEvent(String email, String displayName, String locale) {
}
