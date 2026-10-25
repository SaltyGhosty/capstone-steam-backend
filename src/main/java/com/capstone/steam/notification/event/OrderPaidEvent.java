package com.capstone.steam.notification.event;

import java.math.BigDecimal;
import java.util.List;

/**
 * Ordine pagato: fa partire la ricevuta per email dopo il commit.
 *
 * <p>Le righe si costruiscono dentro la transazione del pagamento: i {@code Game} sono LAZY e il
 * listener gira AFTER_COMMIT in un altro thread, ormai senza sessione Hibernate. Per lo stesso motivo, {@code locale}
 * (lingua dell'email: es, en, it) si legge dall'utente prima di pubblicare.
 */
public record OrderPaidEvent(
        Long orderId,
        Long userId,
        String email,
        String displayName,
        BigDecimal total,
        List<Line> lines,
        String locale) {

    /**
     * Una riga della ricevuta.
     *
     * @param title     titolo del gioco
     * @param coverUrl  copertina così come l'ha salvata il developer (può essere {@code null}; il template la valida)
     * @param unitPrice prezzo pagato, con lo sconto già applicato ({@code OrderItem.unitPrice})
     */
    public record Line(String title, String coverUrl, BigDecimal unitPrice) {
    }
}
