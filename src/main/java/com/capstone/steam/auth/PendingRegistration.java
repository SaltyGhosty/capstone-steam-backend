package com.capstone.steam.auth;

/**
 * I dati di una richiesta di registrazione che viaggiano con il link di verifica e si applicano
 * all'account quando il proprietario dell'email lo clicca.
 */
public record PendingRegistration(String username, String passwordHash, String displayName, String locale,
        boolean developer) {
}
