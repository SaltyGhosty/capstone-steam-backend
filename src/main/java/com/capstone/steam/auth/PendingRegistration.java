package com.capstone.steam.auth;

/**
 * Los datos de una petición de registro que viajan con su enlace de verificación y se aplican a la
 * cuenta cuando el dueño del email lo pulsa.
 */
public record PendingRegistration(String username, String passwordHash, String displayName, String locale,
        boolean developer) {
}
