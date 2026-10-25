package com.capstone.steam.notification;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.Instant;
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
import org.springframework.web.client.RestClientException;

import com.capstone.steam.notification.EmailTemplates.Brand;
import com.capstone.steam.notification.EmailTemplates.Email;
import com.capstone.steam.notification.event.OrderPaidEvent;
import com.capstone.steam.user.DeletionRequester;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/**
 * Invio tramite l'API HTTP di Brevo ({@code BREVO_API_KEY}, quella di produzione: Render free blocca l'SMTP)
 * o, senza quella chiave, tramite SMTP (Gmail). Non lancia mai eccezioni: un'email che fallisce viene registrata nel log
 * e non influisce sull'operazione di business, che ha già fatto commit.
 *
 * <p>Senza {@code BREVO_API_KEY} né {@code MAIL_USERNAME}, l'email resta disattivata e viene solo
 * registrata nel log, per poter sviluppare senza un account email.
 *
 * <p>L'email arriva con il nome del mittente ({@code MAIL_FROM_NAME}, di default «ARCADE») e i suoi
 * pulsanti puntano al frontend ({@code FRONTEND_URL}; se manca, la prima origine di
 * {@code CORS_ALLOWED_ORIGINS}).
 *
 * <p>Ogni email esce nella lingua del destinatario ({@code users.locale}, che viaggia nell'evento).
 */
@Service
public class EmailService {

    static final String DEFAULT_FROM_NAME = "ARCADE";

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final List<String> RESERVED_TLDS = List.of("local", "test", "example", "invalid", "localhost");
    private static final List<String> RESERVED_DOMAINS = List.of("example.com", "example.net", "example.org");

    private final JavaMailSender mailSender;
    /** null se non c'è {@code BREVO_API_KEY}: allora si usa l'SMTP. */
    private final BrevoMailClient brevo;
    private final String from;
    private final Brand brand;
    private final boolean enabled;

    /** Pausa tra i tentativi; nei test viene sostituita per non aspettare davvero. */
    @FunctionalInterface
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    private static final int MAX_ATTEMPTS = 3;
    /** Pausa prima del 2º e del 3º tentativo. */
    private static final List<Duration> RETRY_PAUSES = List.of(Duration.ofSeconds(2), Duration.ofSeconds(6));

    private final Sleeper sleeper;

    @Autowired
    public EmailService(JavaMailSender mailSender, BrevoMailClient brevo,
            @Value("${app.mail.from:}") String from,
            @Value("${app.mail.from-name:}") String fromName,
            @Value("${app.mail.store-url:}") String storeUrl,
            @Value("${spring.mail.username:}") String smtpUsername) {
        this(mailSender, brevo, from, fromName, storeUrl, smtpUsername, Thread::sleep);
    }

    /** Solo SMTP (test). */
    public EmailService(JavaMailSender mailSender, String from, String fromName, String storeUrl, String smtpUsername) {
        this(mailSender, null, from, fromName, storeUrl, smtpUsername, Thread::sleep);
    }

    EmailService(JavaMailSender mailSender, String from, String fromName, String storeUrl, String smtpUsername,
            Sleeper sleeper) {
        this(mailSender, null, from, fromName, storeUrl, smtpUsername, sleeper);
    }

    EmailService(JavaMailSender mailSender, BrevoMailClient brevo, String from, String fromName, String storeUrl,
            String smtpUsername, Sleeper sleeper) {
        this.sleeper = sleeper;
        this.mailSender = mailSender;
        this.brevo = brevo != null && brevo.enabled() ? brevo : null;
        this.from = from == null || from.isBlank() ? smtpUsername : from.trim(); // MAIL_FROM= vuoto o con spazi nel .env
        this.brand = new Brand(
                fromName == null || fromName.isBlank() ? DEFAULT_FROM_NAME : fromName.trim(), // MAIL_FROM_NAME= vuoto
                firstUrl(storeUrl));
        this.enabled = this.brevo != null || !smtpUsername.isBlank();
        if (this.brevo != null) {
            log.info("Correo por la API HTTP de Brevo (BREVO_API_KEY): el SMTP no se usa");
        } else if (!enabled) {
            log.info("Correo desactivado: ni BREVO_API_KEY ni MAIL_USERNAME están configurados (los envíos solo se registran en el log)");
        }
    }

    /** {@code locale}: es, en o it (quello di {@code users.locale}); qualsiasi altro valore, o null, esce in spagnolo. */
    public void sendWelcome(String to, String displayName, String locale, String verificationToken,
            String username) {
        if (verificationToken != null && !enabled) {
            // Solo con l'email disattivata (sviluppo) il link esce nel log, per poterlo verificare
            String path = EmailTemplates.verificationPath(verificationToken);
            log.info("[correo desactivado] enlace de verificación para {}: {}", maskEmail(to),
                    brand.storeUrl() == null ? path : brand.link(path));
        }
        if (verificationToken != null && brand.storeUrl() == null) {
            // senza FRONTEND_URL né un'origine http in CORS l'email esce senza pulsante e non si può verificare
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

    public void sendDeletionScheduled(String to, String displayName, String locale, Instant scheduledAt,
            DeletionRequester requestedBy) {
        send(to, EmailTemplates.deletionScheduled(brand, displayName, locale, scheduledAt, requestedBy));
    }

    public void sendDeletionCancelled(String to, String displayName, String locale) {
        send(to, EmailTemplates.deletionCancelled(brand, displayName, locale));
    }

    public void sendAccountDeleted(String to, String displayName, String locale) {
        send(to, EmailTemplates.accountDeleted(brand, displayName, locale));
    }

    /** Avviso al supporto: va all'indirizzo di assistenza e rispondendo si scrive all'utente ({@code replyTo}). */
    public void sendSupportRequest(String supportAddress, String fromEmail, String displayName, String username,
            String subject, String message, String locale) {
        send(supportAddress, EmailTemplates.supportRequest(brand, fromEmail, displayName, username, locale, subject,
                message), fromEmail);
    }

    /** Conferma di ricezione all'utente, nella sua lingua, con una copia dell'oggetto. */
    public void sendSupportConfirmation(String to, String displayName, String subject, String locale) {
        send(to, EmailTemplates.supportConfirmation(brand, displayName, locale, subject));
    }

    private void send(String to, Email email) {
        send(to, email, null);
    }

    private void send(String to, Email email, String replyTo) {
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
                if (brevo != null) {
                    brevo.send(from, brand.name(), to, email.subject(), email.html(), replyTo);
                } else {
                    MimeMessage message = mailSender.createMimeMessage();
                    MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
                    helper.setFrom(from, brand.name());
                    helper.setTo(to);
                    if (replyTo != null) {
                        helper.setReplyTo(replyTo);
                    }
                    helper.setSubject(email.subject());
                    helper.setText(email.html(), true);
                    mailSender.send(message);
                }
                log.info("Correo enviado: para={} asunto=\"{}\"", masked, email.subject());
                return;
            } catch (MailException | MessagingException | UnsupportedEncodingException | RestClientException ex) {
                // il server spesso ripete l'indirizzo nel suo errore ("Invalid Addresses: ...")
                String reason = ex.getMessage() == null ? null : ex.getMessage().replace(to, masked);
                boolean retryable = brevo != null ? BrevoMailClient.isRetryable(ex) : isConnectionFailure(ex);
                if (attempt < MAX_ATTEMPTS && retryable) {
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
     * Errore di rete parlando con l'SMTP (connessione rifiutata o scaduta): vale un nuovo tentativo.
     * Credenziali o destinatario errati non cambiano aspettando, quindi non si ritentano.
     */
    static boolean isConnectionFailure(Throwable ex) {
        if (!(ex instanceof MailSendException)) {
            return false;
        }
        for (Throwable cause = ex; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
            // MailConnectException appartiene all'implementazione di Jakarta Mail (solo a runtime): si confronta per nome
            if (cause.getClass().getSimpleName().equals("MailConnectException") || cause instanceof ConnectException
                    || cause instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }

    /**
     * L'email del destinatario è un dato personale: nel log compaiono solo la prima lettera e il
     * dominio ("l***@gmail.com"). Senza chiocciola o vuota, "***".
     */
    static String maskEmail(String email) {
        int at = email == null ? -1 : email.lastIndexOf('@');
        return at < 1 ? "***" : email.charAt(0) + "***" + email.substring(at);
    }

    /**
     * Domini che non ricevono mai email (RFC 2606 e 6761): gli account del catalogo demo usano
     * {@code @demo.capstone-steam.local}. Inviare loro qualcosa genera solo rimbalzi nella casella Gmail.
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
     * Prima URL http(s) di una lista separata da virgole (il formato di {@code CORS_ALLOWED_ORIGINS}),
     * senza slash finale. {@code null} se non ce n'è nessuna: allora le email escono senza pulsanti.
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
