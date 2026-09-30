package com.capstone.steam.notification.event;

import java.math.BigDecimal;

/** Pago rechazado: la transacción SÍ hace commit (se guarda el pedido FAILED) y se avisa por correo. */
public record PaymentFailedEvent(
        Long orderId,
        Long userId,
        String email,
        String displayName,
        BigDecimal total,
        String reason) {
}
