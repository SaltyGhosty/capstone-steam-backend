package com.capstone.steam.notification;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import com.capstone.steam.notification.EmailTemplates.Brand;
import com.capstone.steam.notification.EmailTemplates.Email;
import com.capstone.steam.notification.event.OrderPaidEvent;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/**
 * Envío por SMTP (Gmail). Nunca lanza excepciones: un correo que falla se registra en el log
 * y no afecta a la operación de negocio, que ya hizo commit.
 *
 * <p>Si no hay credenciales ({@code MAIL_USERNAME} vacío), el correo queda desactivado y solo se
 * registra en el log, para poder desarrollar sin cuenta de Gmail.
 *
 * <p>El correo llega con nombre de remitente ({@code MAIL_FROM_NAME}, por defecto «ARCADE») y sus
 * botones apuntan al frontend ({@code FRONTEND_URL}; si falta, el primer origen de
 * {@code CORS_ALLOWED_ORIGINS}).
 *
 * <p>Cada correo sale en el idioma del destinatario ({@code users.locale}, que viaja en el evento).
 */
@Service
public class EmailService {

    static final String DEFAULT_FROM_NAME = "ARCADE";

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final List<String> RESERVED_TLDS = List.of("local", "test", "example", "invalid", "localhost");
    private static final List<String> RESERVED_DOMAINS = List.of("example.com", "example.net", "example.org");

    private final JavaMailSender mailSender;
    private final String from;
    private final Brand brand;
    private final boolean enabled;

    public EmailService(JavaMailSender mailSender,
            @Value("${app.mail.from:}") String from,
            @Value("${app.mail.from-name:}") String fromName,
            @Value("${app.mail.store-url:}") String storeUrl,
            @Value("${spring.mail.username:}") String smtpUsername) {
        this.mailSender = mailSender;
        this.from = from == null || from.isBlank() ? smtpUsername : from.trim(); // MAIL_FROM= vacío o con espacios en .env
        this.brand = new Brand(
                fromName == null || fromName.isBlank() ? DEFAULT_FROM_NAME : fromName.trim(), // MAIL_FROM_NAME= vacío
                firstUrl(storeUrl));
        this.enabled = !smtpUsername.isBlank();
        if (!enabled) {
            log.info("Correo desactivado: MAIL_USERNAME no está configurado (los envíos solo se registran en el log)");
        }
    }

    /** {@code locale}: es, en o it (el de {@code users.locale}); cualquier otro valor, o null, sale en español. */
    public void sendWelcome(String to, String displayName, String locale) {
        send(to, EmailTemplates.welcome(brand, displayName, locale));
    }

    public void sendPurchaseReceipt(String to, String displayName, Long orderId, BigDecimal total,
            List<OrderPaidEvent.Line> lines, String locale) {
        send(to, EmailTemplates.purchaseReceipt(brand, displayName, orderId, total, lines, locale));
    }

    public void sendPaymentFailed(String to, String displayName, Long orderId, BigDecimal total, String reason,
            String locale) {
        send(to, EmailTemplates.paymentFailed(brand, displayName, orderId, total, reason, locale));
    }

    public void sendGameOnSale(String to, String displayName, String title, String slug, String coverUrl,
            BigDecimal price, BigDecimal finalPrice, int discountPercent, String locale) {
        send(to, EmailTemplates.gameOnSale(brand, displayName, title, slug, coverUrl, price, finalPrice,
                discountPercent, locale));
    }

    private void send(String to, Email email) {
        String masked = maskEmail(to);
        if (!enabled) {
            log.info("[correo desactivado] para={} asunto=\"{}\"", masked, email.subject());
            return;
        }
        if (isReservedDomain(to)) {
            log.info("[correo no enviado: dominio reservado] para={} asunto=\"{}\"", masked, email.subject());
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(from, brand.name());
            helper.setTo(to);
            helper.setSubject(email.subject());
            helper.setText(email.html(), true);
            mailSender.send(message);
            log.info("Correo enviado: para={} asunto=\"{}\"", masked, email.subject());
        } catch (MailException | MessagingException | UnsupportedEncodingException ex) {
            // el servidor SMTP suele repetir la dirección en su error ("Invalid Addresses: ...")
            String reason = ex.getMessage() == null ? null : ex.getMessage().replace(to, masked);
            log.error("No se pudo enviar el correo a {} (\"{}\"): {}", masked, email.subject(), reason);
        }
    }

    /**
     * El email del destinatario es un dato personal: en el log solo sale la primera letra y el
     * dominio ("l***@gmail.com"). Sin arroba o vacío, "***".
     */
    static String maskEmail(String email) {
        int at = email == null ? -1 : email.lastIndexOf('@');
        return at < 1 ? "***" : email.charAt(0) + "***" + email.substring(at);
    }

    /**
     * Dominios que nunca reciben correo (RFC 2606 y 6761): las cuentas del catálogo demo usan
     * {@code @demo.capstone-steam.local}. Enviarles nada solo genera rebotes en el buzón de Gmail.
     */
    static boolean isReservedDomain(String to) {
        int at = to == null ? -1 : to.lastIndexOf('@');
        if (at < 0) {
            return true;
        }
        String domain = to.substring(at + 1).toLowerCase(Locale.ROOT);
        return RESERVED_TLDS.stream().anyMatch(tld -> domain.equals(tld) || domain.endsWith("." + tld))
                || RESERVED_DOMAINS.stream().anyMatch(d -> domain.equals(d) || domain.endsWith("." + d));
    }

    /**
     * Primera URL http(s) de una lista separada por comas (el formato de {@code CORS_ALLOWED_ORIGINS}),
     * sin barra final. {@code null} si no hay ninguna: los correos salen entonces sin botones.
     */
    static String firstUrl(String value) {
        if (value == null) {
            return null;
        }
        for (String candidate : value.split(",")) {
            String url = candidate.trim().replaceAll("/+$", "");
            if (url.startsWith("http://") || url.startsWith("https://")) {
                return url;
            }
        }
        return null;
    }
}
