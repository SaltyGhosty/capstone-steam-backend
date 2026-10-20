package com.capstone.steam.notification;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.capstone.steam.common.i18n.Messages;
import com.capstone.steam.config.AsyncConfig;
import com.capstone.steam.notification.event.GameOnSaleEvent;
import com.capstone.steam.notification.event.OrderPaidEvent;
import com.capstone.steam.notification.event.PaymentFailedEvent;
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

    private final EmailService emailService;

    public MailListener(EmailService emailService) {
        this.emailService = emailService;
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
                event.slug(), event.coverUrl(), event.price(), event.finalPrice(), event.discountPercent(), r.locale()));
    }
}
