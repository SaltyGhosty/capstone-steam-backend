package com.capstone.steam.notification.event;

import java.math.BigDecimal;

/**
 * Pagamento rifiutato: la transazione FA commit (si salva l'ordine FAILED) e si avvisa per email.
 *
 * @param reason chiave del motivo dato dal gateway (payment.declined.*); si traduce nella lingua dell'email
 * @param locale lingua dell'email (es, en, it), letta dall'utente dentro la transazione
 */
public record PaymentFailedEvent(
        Long orderId,
        Long userId,
        String email,
        String displayName,
        BigDecimal total,
        String reason,
        String locale) {
}
