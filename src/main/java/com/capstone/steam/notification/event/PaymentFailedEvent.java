package com.capstone.steam.notification.event;

import java.math.BigDecimal;

/**
 * Pago rechazado: la transacción SÍ hace commit (se guarda el pedido FAILED) y se avisa por correo.
 *
 * @param reason clave del motivo que da la pasarela (payment.declined.*); se traduce al idioma del correo
 * @param locale idioma del correo (es, en, it), leído del usuario dentro de la transacción
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
