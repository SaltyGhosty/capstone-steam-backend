package com.capstone.steam.notification.event;

/** Se publica dentro de la transacción que cancela una baja pendiente; el correo sale en AFTER_COMMIT. */
public record AccountDeletionCancelledEvent(String email, String displayName, String locale) {
}
