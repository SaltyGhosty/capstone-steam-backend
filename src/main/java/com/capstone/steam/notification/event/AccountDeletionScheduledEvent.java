package com.capstone.steam.notification.event;

import java.time.Instant;

import com.capstone.steam.user.DeletionRequester;

/**
 * Se publica dentro de la transacción que programa la baja; el listener de correo lo consume en AFTER_COMMIT.
 * Solo datos primitivos, nunca entidades.
 *
 * @param scheduledAt cuándo se anonimizará la cuenta
 * @param requestedBy quién pidió la baja (decide si el correo ofrece recuperarla)
 */
public record AccountDeletionScheduledEvent(String email, String displayName, String locale, Instant scheduledAt,
        DeletionRequester requestedBy) {
}
