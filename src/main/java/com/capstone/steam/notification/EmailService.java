package com.capstone.steam.notification;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
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

    /** Pausa entre intentos; en los tests se sustituye para no esperar de verdad. */
    @FunctionalInterface
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    private static final int MAX_ATTEMPTS = 3;
    /** Pausa antes del 2.º y del 3.º intento. */
    private static final List<Duration> RETRY_PAUSES = List.of(Duration.ofSeconds(2), Duration.ofSeconds(6));

    private final Sleeper sleeper;

    @Autowired
    public EmailService(JavaMailSender mailSender,
            @Value("${app.mail.from:}") String from,
            @Value("${app.mail.from-name:}") String fromName,
            @Value("${app.mail.store-url:}") String storeUrl,
            @Value("${spring.mail.username:}") String smtpUsername) {
        this(mailSender, from, fromName, storeUrl, smtpUsername, Thread::sleep);
    }

    EmailService(JavaMailSender mailSender, String from, String fromName, String storeUrl, String smtpUsername,
            Sleeper sleeper) {
        this.sleeper = sleeper;
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
    public void sendWelcome(String to, String displayName, String locale, String verificationToken,
            String username) {
        if (verificationToken != null && !enabled) {
            // Solo con el correo desactivado (desarrollo) el enlace sale en el log, para poder verificarse
            String path = EmailTemplates.verificationPath(verificationToken);
            log.info("[correo desactivado] enlace de verificación para {}: {}", maskEmail(to),
                    brand.storeUrl() == null ? path : brand.link(path));
        }
        if (verificationToken != null && brand.storeUrl() == null) {
            // sin FRONTEND_URL ni un origen http en CORS el correo sale sin botón y no se puede verificar
            log.warn("El correo de verificación para {} sale sin enlace: falta FRONTEND_URL (o un origen http en CORS_ALLOWED_ORIGINS)",
                    maskEmail(to));
        }
        send(to, EmailTemplates.welcome(brand, displayName, locale, verificationToken, username));
    }

    public void sendPurchaseReceipt(String to, String displayName, Long orderId, BigDecimal total,
            List<OrderPaidEvent.Line> lines, String locale) {
        send(to, EmailTemplates.purchaseReceipt(brand, displayName, orderId, total, lines, locale));
    }

    public void sendPaymentFailed(String to, String displayName, Long orderId, BigDecimal total, String reason,
            String locale) {
        send(to, EmailTemplates.paymentFailed(brand, displayName, orderId, total, reason, locale));
    }

    public void sendGameOnSale(String to, String displayName, String title, String coverUrl,
            BigDecimal price, BigDecimal finalPrice, int discountPercent, String locale) {
        send(to, EmailTemplates.gameOnSale(brand, displayName, title, coverUrl, price, finalPrice,
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
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
                helper.setFrom(from, brand.name());
                helper.setTo(to);
                helper.setSubject(email.subject());
                helper.setText(email.html(), true);
                mailSender.send(message);
                log.info("Correo enviado: para={} asunto=\"{}\"", masked, email.subject());
                return;
            } catch (MailException | MessagingException | UnsupportedEncodingException ex) {
                // el servidor SMTP suele repetir la dirección en su error ("Invalid Addresses: ...")
                String reason = ex.getMessage() == null ? null : ex.getMessage().replace(to, masked);
                if (attempt < MAX_ATTEMPTS && isConnectionFailure(ex)) {
                    log.warn("Reintento {}/{} del correo a {} (\"{}\"): {}", attempt + 1, MAX_ATTEMPTS, masked,
                            email.subject(), reason);
                    try {
                        sleeper.sleep(RETRY_PAUSES.get(attempt - 1));
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        log.error("Correo a {} (\"{}\") cancelado: el hilo fue interrumpido", masked, email.subject());
                        return;
                    }
                    continue;
                }
                log.error("No se pudo enviar el correo a {} (\"{}\"): {}", masked, email.subject(), reason);
                return;
            }
        }
    }

    /**
     * Fallo de red al hablar con el SMTP (conexión rechazada o agotada): merece reintento.
     * Las credenciales o el destinatario incorrectos no cambian por esperar, no se reintentan.
     */
    static boolean isConnectionFailure(Throwable ex) {
        if (!(ex instanceof MailSendException)) {
            return false;
        }
        for (Throwable cause = ex; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
            // MailConnectException es de la implementación de Jakarta Mail (solo en runtime): se compara por nombre
            if (cause.getClass().getSimpleName().equals("MailConnectException") || cause instanceof ConnectException
                    || cause instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
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
