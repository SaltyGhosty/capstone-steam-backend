package com.capstone.steam.notification.event;

import java.math.BigDecimal;
import java.util.List;

/**
 * Pedido pagado: dispara el recibo por correo tras el commit.
 *
 * <p>Las líneas se construyen dentro de la transacción del pago: los {@code Game} son LAZY y el
 * listener corre AFTER_COMMIT en otro hilo, ya sin sesión de Hibernate. Por lo mismo, {@code locale}
 * (idioma del correo: es, en, it) se lee del usuario antes de publicar.
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
     * Una línea del recibo.
     *
     * @param title     título del juego
     * @param coverUrl  portada tal como la guardó el developer (puede ser {@code null}; la plantilla la valida)
     * @param unitPrice precio pagado, con el descuento ya aplicado ({@code OrderItem.unitPrice})
     */
    public record Line(String title, String coverUrl, BigDecimal unitPrice) {
    }
}
