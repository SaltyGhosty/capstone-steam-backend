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
 * Traduce gli eventi di dominio in email.
 * <ul>
 *   <li><b>AFTER_COMMIT</b>: si invia solo se la transazione che ha pubblicato l'evento ha fatto commit.
 *       Una registrazione o un acquisto che fa rollback non genera email.</li>
 *   <li><b>@Async("mailExecutor")</b>: l'invio va al pool dedicato; la richiesta HTTP risponde
 *       senza aspettare l'SMTP.</li>
 * </ul>
 */
@Component
public class MailListener {

    private static final Logger log = LoggerFactory.getLogger(MailListener.class);

    private final EmailService emailService;
    /** Casella di assistenza ({@code SUPPORT_EMAIL}); vuota = non si avvisa il supporto. */
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

    /** Reinvio del link di verifica (stessa email del benvenuto con verifica). */
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

    /** Un'email per ogni utente che ha il gioco nella wishlist, nella sua lingua. */
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

    /** Va all'email che l'account aveva prima di essere anonimizzato (la porta l'evento). */
    @Async(AsyncConfig.MAIL_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAccountDeleted(AccountDeletedEvent event) {
        emailService.sendAccountDeleted(event.email(), event.displayName(), event.locale());
    }

    /**
     * Form di assistenza: avviso al supporto (con Reply-To all'utente) e conferma di ricezione all'utente. L'evento non
     * nasce in una transazione, quindi non c'è AFTER_COMMIT: si ascolta direttamente e l'invio va al pool delle email.
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
