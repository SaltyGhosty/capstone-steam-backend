package com.capstone.steam.notification.event;

import java.math.BigDecimal;
import java.util.List;

/** Pedido pagado: dispara el recibo por correo tras el commit. */
public record OrderPaidEvent(
        Long orderId,
        Long userId,
        String email,
        String displayName,
        BigDecimal total,
        List<String> gameTitles) {
}
