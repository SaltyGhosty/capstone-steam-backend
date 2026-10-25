package com.capstone.steam.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.capstone.steam.common.i18n.Messages;
import com.capstone.steam.config.AsyncConfig;
import com.capstone.steam.notification.event.AccountDeletedEvent;
import com.capstone.steam.notification.event.AccountDeletionCancelledEvent;
import com.capstone.steam.notification.event.AccountDeletionScheduledEvent;
import com.capstone.steam.notification.event.GameOnSaleEvent;
import com.capstone.steam.notification.event.OrderPaidEvent;
import com.capstone.steam.notification.event.PaymentFailedEvent;
import com.capstone.steam.notification.event.SupportRequestEvent;
import com.capstone.steam.notification.event.UserRegisteredEvent;
import com.capstone.steam.notification.event.VerificationEmailEvent;

/**
 * Traduce eventos de dominio en correos.
 * <ul>
 *   <li><b>AFTER_COMMIT</b>: solo se envía si la transacción que publicó el evento hizo commit.
 *       Un registro o una compra que hace rollback no genera correo.</li>
 *   <li><b>@Async("mailExecutor")</b>: el envío va al pool dedicado; la petición HTTP responde
 *       sin esperar al SMTP.</li>
 * </ul>
 */
@Component
public class MailListener {

    private static final Logger log = LoggerFactory.getLogger(MailListener.class);

    private final EmailService emailService;
    /** Buzón de asistencia ({@code SUPPORT_EMAIL}); vacío = no se avisa a soporte. */
    private final String supportAddress;

    public MailListener(EmailService emailService, @Value("${app.support.email:}") String supportAddress) {
        this.emailService = emailService;
        this.supportAddress = supportAddress == null ? "" : supportAddress.trim();
    }

    @Async(AsyncConfig.MAIL_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserRegistered(UserRegisteredEvent event) {
        emailService.sendWelcome(event.email(), event.displayName(), event.locale(), event.verificationToken(),
                event.username());
    }

    /** Reenvío del enlace de verificación (mismo correo que la bienvenida con verificación). */
    @Async(AsyncConfig.MAIL_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVerificationEmail(VerificationEmailEvent event) {
        emailService.sendWelcome(event.email(), event.displayName(), event.locale(), event.verificationToken(),
                event.username());
    }

    @Async(AsyncConfig.MAIL_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPaid(OrderPaidEvent event) {
        emailService.sendPurchaseReceipt(event.email(), event.displayName(), event.orderId(), event.total(),
                event.lines(), event.locale());
    }

    @Async(AsyncConfig.MAIL_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentFailed(PaymentFailedEvent event) {
        emailService.sendPaymentFailed(event.email(), event.displayName(), event.orderId(), event.total(),
                Messages.getOrSelf(Messages.locale(event.locale()), event.reason()), event.locale());
    }

    /** Un correo por cada usuario que tiene el juego en su wishlist, en su idioma. */
    @Async(AsyncConfig.MAIL_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGameOnSale(GameOnSaleEvent event) {
        event.recipients().forEach(r -> emailService.sendGameOnSale(r.email(), r.displayName(), event.title(),
                event.coverUrl(), event.price(), event.finalPrice(), event.discountPercent(), r.locale()));
    }

    @Async(AsyncConfig.MAIL_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAccountDeletionScheduled(AccountDeletionScheduledEvent event) {
        emailService.sendDeletionScheduled(event.email(), event.displayName(), event.locale(), event.scheduledAt(),
                event.requestedBy());
    }

    @Async(AsyncConfig.MAIL_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAccountDeletionCancelled(AccountDeletionCancelledEvent event) {
        emailService.sendDeletionCancelled(event.email(), event.displayName(), event.locale());
    }

    /** Va al email que la cuenta tenía antes de anonimizarse (el evento lo lleva). */
    @Async(AsyncConfig.MAIL_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAccountDeleted(AccountDeletedEvent event) {
        emailService.sendAccountDeleted(event.email(), event.displayName(), event.locale());
    }

    /**
     * Formulario de asistencia: aviso a soporte (con Reply-To al usuario) y acuse de recibo al usuario. El evento no
     * nace en una transacción, así que no hay AFTER_COMMIT: se escucha directamente y el envío va al pool de correo.
     */
    @Async(AsyncConfig.MAIL_EXECUTOR)
    @EventListener
    public void onSupportRequest(SupportRequestEvent event) {
        if (supportAddress.isEmpty()) {
            log.warn("Mensaje de asistencia sin SUPPORT_EMAIL (ni MAIL_FROM ni MAIL_USERNAME): no se avisa a soporte");
        } else {
            emailService.sendSupportRequest(supportAddress, event.fromEmail(), event.displayName(), event.username(),
                    event.subject(), event.message(), event.locale());
        }
        emailService.sendSupportConfirmation(event.fromEmail(), event.displayName(), event.subject(), event.locale());
    }
}
