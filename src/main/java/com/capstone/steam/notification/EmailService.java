package com.capstone.steam.notification;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import com.capstone.steam.notification.EmailTemplates.Email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/**
 * Envío por SMTP (Gmail). Nunca lanza excepciones: un correo que falla se registra en el log
 * y no afecta a la operación de negocio, que ya hizo commit.
 *
 * <p>Si no hay credenciales ({@code MAIL_USERNAME} vacío), el correo queda desactivado y solo se
 * registra en el log, para poder desarrollar sin cuenta de Gmail.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final String from;
    private final boolean enabled;

    public EmailService(JavaMailSender mailSender,
            @Value("${app.mail.from:}") String from,
            @Value("${spring.mail.username:}") String smtpUsername) {
        this.mailSender = mailSender;
        this.from = from == null || from.isBlank() ? smtpUsername : from; // MAIL_FROM= vacío en .env
        this.enabled = !smtpUsername.isBlank();
        if (!enabled) {
            log.info("Correo desactivado: MAIL_USERNAME no está configurado (los envíos solo se registran en el log)");
        }
    }

    public void sendWelcome(String to, String displayName) {
        send(to, EmailTemplates.welcome(displayName));
    }

    public void sendPurchaseReceipt(String to, String displayName, Long orderId, BigDecimal total,
            List<String> gameTitles) {
        send(to, EmailTemplates.purchaseReceipt(displayName, orderId, total, gameTitles));
    }

    public void sendPaymentFailed(String to, String displayName, Long orderId, BigDecimal total, String reason) {
        send(to, EmailTemplates.paymentFailed(displayName, orderId, total, reason));
    }

    private void send(String to, Email email) {
        if (!enabled) {
            log.info("[correo desactivado] para={} asunto=\"{}\"", to, email.subject());
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(email.subject());
            helper.setText(email.html(), true);
            mailSender.send(message);
            log.info("Correo enviado: para={} asunto=\"{}\"", to, email.subject());
        } catch (MailException | MessagingException ex) {
            log.error("No se pudo enviar el correo a {} (\"{}\"): {}", to, email.subject(), ex.getMessage());
        }
    }
}
