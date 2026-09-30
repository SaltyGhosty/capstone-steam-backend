package com.capstone.steam.notification;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.springframework.web.util.HtmlUtils;

/**
 * Plantillas HTML de los correos. Todo valor que viene del usuario se escapa
 * (un displayName "&lt;script&gt;" no debe inyectar HTML en el correo).
 */
final class EmailTemplates {

    private static final Locale ES = Locale.forLanguageTag("es-ES");

    private EmailTemplates() {
    }

    record Email(String subject, String html) {
    }

    static Email welcome(String displayName) {
        return new Email("¡Bienvenido a Capstone Steam!", layout("""
                <h1>¡Hola, %s!</h1>
                <p>Tu cuenta se ha creado correctamente. Ya puedes explorar el catálogo,
                   guardar juegos en tu lista de deseos y empezar tu biblioteca.</p>
                """.formatted(esc(displayName))));
    }

    static Email purchaseReceipt(String displayName, Long orderId, BigDecimal total, List<String> gameTitles) {
        String items = gameTitles.stream()
                .map(t -> "<li>" + esc(t) + "</li>")
                .collect(Collectors.joining());
        return new Email("Recibo de tu compra #" + orderId, layout("""
                <h1>¡Gracias por tu compra, %s!</h1>
                <p>Pedido <strong>#%d</strong> pagado correctamente. Estos juegos ya están en tu biblioteca:</p>
                <ul>%s</ul>
                <p style="font-size:18px">Total: <strong>%s</strong></p>
                """.formatted(esc(displayName), orderId, items, money(total))));
    }

    static Email paymentFailed(String displayName, Long orderId, BigDecimal total, String reason) {
        return new Email("No hemos podido cobrar tu pedido #" + orderId, layout("""
                <h1>Hola, %s</h1>
                <p>El pago de tu pedido <strong>#%d</strong> (%s) ha sido rechazado:</p>
                <p style="color:#b3261e"><strong>%s</strong></p>
                <p>Los juegos siguen en tu carrito: puedes volver a intentarlo con otro método de pago.</p>
                """.formatted(esc(displayName), orderId, money(total), esc(reason))));
    }

    private static String layout(String body) {
        return """
                <!doctype html>
                <html lang="es"><body style="font-family:Arial,sans-serif;background:#1b2838;padding:24px">
                  <div style="max-width:560px;margin:auto;background:#ffffff;border-radius:8px;padding:24px;color:#1b2838">
                    %s
                    <hr style="border:none;border-top:1px solid #ddd">
                    <p style="font-size:12px;color:#777">Capstone Steam · Este es un correo automático, no respondas.</p>
                  </div>
                </body></html>
                """.formatted(body);
    }

    static String money(BigDecimal amount) {
        return NumberFormat.getCurrencyInstance(ES).format(amount);
    }

    private static String esc(String value) {
        return value == null ? "" : HtmlUtils.htmlEscape(value);
    }
}
