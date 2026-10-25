package com.capstone.steam.notification.event;

/**
 * Mensaje del formulario de asistencia. Se publica fuera de una transacción (no se guarda nada en la base de
 * datos), así que el listener lo recibe al momento. {@code displayName} y {@code username} son null si quien
 * escribe no tiene sesión. Solo datos primitivos, nunca entidades.
 */
public record SupportRequestEvent(String fromEmail, String displayName, String username, String subject,
        String message, String locale) {
}
