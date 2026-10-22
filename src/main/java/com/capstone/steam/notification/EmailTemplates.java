package com.capstone.steam.notification;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.springframework.web.util.HtmlUtils;

import com.capstone.steam.notification.event.OrderPaidEvent;
import com.capstone.steam.user.DeletionRequester;

/**
 * Plantillas HTML de los correos, con el aspecto de la tienda (paleta de
 * {@code frontend/src/index.css}). Todo valor que viene del usuario se escapa
 * (un displayName "&lt;script&gt;" no debe inyectar HTML en el correo).
 *
 * <p>El HTML usa tablas y estilos en línea, sin hojas de estilo: es lo único que Gmail, Outlook
 * y el resto de clientes de correo muestran igual.
 *
 * <p>Los textos salen en el idioma del destinatario ({@link MailTexts}); un idioma desconocido cae en
 * español.
 *
 * <p>Las portadas del recibo de compra son la única imagen externa. Solo se enlazan si la URL es
 * http(s) y van con {@code alt}, ancho y alto fijos en atributos: si el cliente bloquea las
 * imágenes, el correo se sigue leyendo bien y las filas no se descuadran.
 */
final class EmailTemplates {

    private static final String BG = "#080b10";
    private static final String PANEL = "#11151b";
    private static final String LINE = "#272d35";
    private static final String TEXT = "#f3f5f7";
    private static final String MUTED = "#9199a5";
    private static final String FAINT = "#69717e";
    private static final String ACCENT = "#b7ff3c";
    private static final String ACCENT_INK = "#0b0f08";
    private static final String DANGER = "#ff7b72";
    private static final String FONT = "Inter,'Segoe UI',Helvetica,Arial,sans-serif";
    /** Zona en la que se da la fecha de la baja: la de la tienda. */
    private static final ZoneId MAIL_ZONE = ZoneId.of("Europe/Rome");

    private EmailTemplates() {
    }

    record Email(String subject, String html) {
    }

    /**
     * Quién firma el correo y adónde llevan sus botones.
     *
     * @param name     nombre de la tienda; es también el nombre visible del remitente
     * @param storeUrl URL del frontend sin barra final, o {@code null} si no se conoce (sin botones)
     */
    record Brand(String name, String storeUrl) {

        String link(String path) {
            return storeUrl == null ? null : storeUrl + path;
        }
    }

    /**
     * Bienvenida. Con {@code verificationToken} es también la confirmación del email (botón a
     * {@code /verify-email}); sin él (verificación desactivada) es la bienvenida de siempre.
     */
    static Email welcome(Brand brand, String displayName, String locale, String verificationToken, String username) {
        MailTexts t = MailTexts.of(locale);
        if (verificationToken != null) {
            String body = eyebrow(t.welcomeEyebrow(), ACCENT)
                    + heading(greeting(t.welcomeHello(), displayName, "!"))
                    + paragraph(t.verifyBody().formatted(esc(username == null ? displayName : username), esc(brand.name())))
                    + button(t.verifyButton(), brand.link(verificationPath(verificationToken)))
                    + paragraph(t.verifyExpiry());
            return new Email(t.verifySubject().formatted(brand.name()),
                    layout(brand, t, t.verifyPreheader(), body));
        }
        String body = eyebrow(t.welcomeEyebrow(), ACCENT)
                + heading(greeting(t.welcomeHello(), displayName, "!"))
                + paragraph(t.welcomeBody().formatted(esc(brand.name())));
        return new Email(t.welcomeSubject().formatted(brand.name()), layout(brand, t, t.welcomePreheader(), body));
    }

    /** Ruta del enlace de verificación; el token va codificado para URL. */
    static String verificationPath(String token) {
        return "/verify-email?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    static Email purchaseReceipt(Brand brand, String displayName, Long orderId, BigDecimal total,
            List<OrderPaidEvent.Line> lines, String locale) {
        MailTexts t = MailTexts.of(locale);
        String rows = lines.stream().map(line -> receiptRow(line, t)).collect(Collectors.joining());
        String intro = lines.size() == 1 ? t.receiptIntroOne() : t.receiptIntroMany();
        String body = eyebrow(t.receiptEyebrow().formatted(orderId), ACCENT)
                + heading(greeting(t.receiptThanks(), displayName, "!"))
                + paragraph(intro)
                + """
                        <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="margin-top:8px;">
                        %s
                        <tr>
                          <td colspan="2" style="padding:16px 0 0;font:400 14px/1.4 %s;color:%s;">%s</td>
                          <td align="right" style="padding:16px 0 0;font:700 20px/1.2 %s;color:%s;">%s</td>
                        </tr>
                        </table>
                        """.formatted(rows, FONT, MUTED, t.total(), FONT, TEXT, money(total, t));
        return new Email(t.receiptSubject().formatted(orderId),
                layout(brand, t, t.receiptPreheader().formatted(orderId, money(total, t)), body));
    }

    /** {@code reason} llega ya traducido al idioma del correo y se muestra escapado. */
    static Email paymentFailed(Brand brand, String displayName, Long orderId, BigDecimal total, String reason,
            String locale) {
        MailTexts t = MailTexts.of(locale);
        String amount = "<strong style=\"color:" + TEXT + ";\">" + money(total, t) + "</strong>";
        String body = eyebrow(t.failedEyebrow().formatted(orderId), DANGER)
                + heading(t.failedHeading())
                + paragraph(greeting(t.failedHello(), displayName, ".") + " " + t.failedIntro().formatted(amount))
                + """
                        <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="margin-top:14px;">
                        <tr><td style="padding:13px 16px;border:1px solid %s;border-radius:9px;font:600 15px/1.45 %s;color:%s;">%s</td></tr>
                        </table>
                        """.formatted(DANGER, FONT, DANGER, esc(reason))
                + paragraph(t.failedRetry());
        return new Email(t.failedSubject().formatted(orderId),
                layout(brand, t, t.failedPreheader().formatted(orderId), body));
    }

    /** Aviso de oferta de un juego de la wishlist: portada, título y precio antes y ahora. */
    static Email gameOnSale(Brand brand, String displayName, String title, String coverUrl,
            BigDecimal price, BigDecimal finalPrice, int discountPercent, String locale) {
        MailTexts t = MailTexts.of(locale);
        String cover = safeImageUrl(coverUrl);
        String image = cover == null ? "" : """
                <img src="%s" width="160" alt="%s" border="0" style="display:block;width:160px;max-width:100%%;border:0;border-radius:8px;">"""
                .formatted(esc(cover), esc(title));
        String body = eyebrow(t.saleEyebrow().formatted(discountPercent), ACCENT)
                + heading(t.saleHeading().formatted(esc(title)))
                + paragraph(greeting(t.failedHello(), displayName, ".") + " " + t.saleBody())
                + """
                        <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="margin-top:14px;">
                        <tr>
                          <td width="172" style="width:172px;padding:0 12px 0 0;vertical-align:middle;">%s</td>
                          <td style="vertical-align:middle;font:600 16px/1.4 %s;color:%s;">%s<br>
                            <span style="font:400 14px/1.6 %s;color:%s;text-decoration:line-through;">%s</span>
                            <span style="font:700 20px/1.4 %s;color:%s;">&nbsp;%s</span>
                          </td>
                        </tr>
                        </table>
                        """.formatted(image, FONT, TEXT, esc(title), FONT, MUTED, money(price, t), FONT, ACCENT,
                        money(finalPrice, t));
        return new Email(t.saleSubject().formatted(title, discountPercent),
                layout(brand, t, t.salePreheader(), body));
    }


    /**
     * Baja programada. Si la pidió el propio usuario, el correo explica cómo recuperar la cuenta; si la programó un
     * admin, pide contestar al correo (no hay botón de recuperación: la cuenta no se recupera iniciando sesión).
     * La fecha va en formato largo del idioma del correo, en la zona de la tienda (Europe/Rome).
     */
    static Email deletionScheduled(Brand brand, String displayName, String locale, Instant scheduledAt,
            DeletionRequester requestedBy) {
        MailTexts t = MailTexts.of(locale);
        String date = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(t.moneyLocale())
                .withZone(MAIL_ZONE).format(scheduledAt);
        String text = requestedBy == DeletionRequester.ADMIN ? t.deletionScheduledByAdmin() : t.deletionScheduledBySelf();
        String body = eyebrow(t.deletionScheduledEyebrow(), DANGER)
                + heading(t.deletionScheduledHeading())
                + paragraph(greeting(t.failedHello(), displayName, ".") + " " + text.formatted(esc(date)));
        return new Email(t.deletionScheduledSubject(), layout(brand, t, t.deletionScheduledPreheader(), body));
    }

    /** La baja pendiente se canceló (por un admin o por el propio usuario al recuperar la cuenta). */
    static Email deletionCancelled(Brand brand, String displayName, String locale) {
        MailTexts t = MailTexts.of(locale);
        String body = eyebrow(t.deletionCancelledEyebrow(), ACCENT)
                + heading(t.deletionCancelledHeading())
                + paragraph(greeting(t.failedHello(), displayName, ".") + " " + t.deletionCancelledBody());
        return new Email(t.deletionCancelledSubject(), layout(brand, t, t.deletionCancelledPreheader(), body));
    }

    /** La cuenta ya está anonimizada; el correo va al email que tenía antes de anonimizar. */
    static Email accountDeleted(Brand brand, String displayName, String locale) {
        MailTexts t = MailTexts.of(locale);
        String body = eyebrow(t.accountDeletedEyebrow(), MUTED)
                + heading(t.accountDeletedHeading())
                + paragraph(greeting(t.failedHello(), displayName, ".") + " " + t.accountDeletedBody());
        return new Email(t.accountDeletedSubject(), layout(brand, t, t.accountDeletedPreheader(), body));
    }

    // ------------------------------------------------------------------
    // Piezas
    // ------------------------------------------------------------------

    private static final int THUMB_WIDTH = 64;
    private static final int THUMB_HEIGHT = 36; // 16:9, como las portadas de la tienda

    /**
     * Fila del recibo: miniatura, título y precio pagado. La celda de la miniatura tiene ancho fijo
     * y existe siempre, así las filas sin portada (o con la imagen bloqueada) no se descuadran.
     */
    private static String receiptRow(OrderPaidEvent.Line line, MailTexts t) {
        String cover = safeImageUrl(line.coverUrl());
        String thumb = cover == null
                ? "&nbsp;"
                : """
                        <img src="%s" width="%d" height="%d" alt="%s" border="0" style="display:block;width:%dpx;height:%dpx;border:0;border-radius:6px;object-fit:cover;background:%s;color:%s;font:400 10px/1.2 %s;">"""
                        .formatted(esc(cover), THUMB_WIDTH, THUMB_HEIGHT, esc(line.title()), THUMB_WIDTH, THUMB_HEIGHT,
                                LINE, MUTED, FONT);
        String price = line.unitPrice() == null ? "" : money(line.unitPrice(), t);
        return """
                <tr>
                  <td width="%d" style="width:%dpx;padding:12px 12px 12px 0;border-bottom:1px solid %s;vertical-align:middle;">%s</td>
                  <td style="padding:12px 0;border-bottom:1px solid %s;vertical-align:middle;font:600 15px/1.4 %s;color:%s;">%s</td>
                  <td align="right" style="padding:12px 0 12px 12px;border-bottom:1px solid %s;vertical-align:middle;white-space:nowrap;font:600 15px/1.4 %s;color:%s;">%s</td>
                </tr>
                """.formatted(THUMB_WIDTH + 12, THUMB_WIDTH + 12, LINE, thumb,
                LINE, FONT, TEXT, esc(line.title()),
                LINE, FONT, TEXT, price);
    }

    /**
     * La portada la escribe un developer y llega al correo de otro usuario: solo se acepta una URL
     * https. Sin TLS, la petición de la imagen viaja en claro. Cualquier otra cosa ({@code http:},
     * {@code javascript:}, {@code data:}, rutas relativas...) se descarta.
     */
    static String safeImageUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String trimmed = url.trim();
        return trimmed.toLowerCase(Locale.ROOT).startsWith("https://") ? trimmed : null;
    }

    /** "¡Hola, Ana!" o, si el usuario no tiene nombre visible, "¡Hola!". El nombre sale escapado. */
    private static String greeting(String opening, String displayName, String closing) {
        boolean anonymous = displayName == null || displayName.isBlank();
        return opening + (anonymous ? "" : ", " + esc(displayName.trim())) + closing;
    }

    private static String eyebrow(String text, String color) {
        return """
                <p style="margin:0 0 12px;font:700 11px/1.4 %s;letter-spacing:.14em;text-transform:uppercase;color:%s;">%s</p>
                """.formatted(FONT, color, esc(text));
    }

    /** {@code html} ya debe venir escapado. */
    private static String heading(String html) {
        return """
                <h1 style="margin:0 0 14px;font:800 26px/1.2 %s;color:%s;">%s</h1>
                """.formatted(FONT, TEXT, html);
    }

    /** {@code html} ya debe venir escapado. */
    private static String paragraph(String html) {
        return """
                <p style="margin:14px 0 0;font:400 15px/1.6 %s;color:%s;">%s</p>
                """.formatted(FONT, MUTED, html);
    }

    private static String button(String label, String url) {
        if (url == null) {
            return "";
        }
        return """
                <table role="presentation" cellpadding="0" cellspacing="0" border="0" style="margin-top:26px;">
                <tr><td style="background:%s;border-radius:9px;">
                  <a href="%s" style="display:inline-block;padding:13px 22px;font:700 14px/1 %s;color:%s;text-decoration:none;">%s</a>
                </td></tr>
                </table>
                """.formatted(ACCENT, esc(url), FONT, ACCENT_INK, esc(label));
    }

    private static String logo(Brand brand) {
        String name = esc(brand.name());
        String wordmark = brand.storeUrl() == null
                ? name
                : "<a href=\"" + esc(brand.storeUrl()) + "\" style=\"color:#ffffff;text-decoration:none;\">" + name + "</a>";
        return """
                <table role="presentation" cellpadding="0" cellspacing="0" border="0">
                <tr>
                  <td style="width:26px;height:26px;border:2px solid %s;border-radius:9px;text-align:center;vertical-align:middle;line-height:0;">
                    <span style="display:inline-block;width:8px;height:8px;background:%s;border-radius:2px;"></span>
                  </td>
                  <td style="padding-left:11px;font:700 16px/1 %s;letter-spacing:.13em;text-transform:uppercase;color:#ffffff;">%s</td>
                </tr>
                </table>
                """.formatted(ACCENT, ACCENT, FONT, wordmark);
    }

    /**
     * Marco común. {@code preheader} es la línea que los clientes de correo enseñan junto al
     * asunto en la bandeja de entrada; dentro del correo va oculta.
     */
    private static String layout(Brand brand, MailTexts t, String preheader, String body) {
        return """
                <!doctype html>
                <html lang="%s">
                <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <meta name="color-scheme" content="dark">
                <meta name="supported-color-schemes" content="dark">
                </head>
                <body style="margin:0;padding:0;background:%s;">
                <div style="display:none;max-height:0;overflow:hidden;opacity:0;color:transparent;">%s</div>
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="background:%s;">
                <tr><td align="center" style="padding:32px 16px;">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="max-width:560px;">
                  <tr><td style="padding:0 4px 20px;">%s</td></tr>
                  <tr><td style="background:%s;border:1px solid %s;border-radius:14px;padding:32px 28px;">%s</td></tr>
                  <tr><td style="padding:18px 4px 0;font:400 12px/1.5 %s;color:%s;">%s · %s</td></tr>
                  </table>
                </td></tr>
                </table>
                </body>
                </html>
                """.formatted(t.lang(), BG, esc(preheader), BG, logo(brand), PANEL, LINE, body, FONT, FAINT, esc(brand.name()),
                        t.footer());
    }

    /** Importe en euros con el formato del idioma: «39,99 €» (es), «€39.99» (en), «39,99 €» (it). */
    static String money(BigDecimal amount, MailTexts texts) {
        return NumberFormat.getCurrencyInstance(texts.moneyLocale()).format(amount);
    }

    private static String esc(String value) {
        return value == null ? "" : HtmlUtils.htmlEscape(value);
    }
}
