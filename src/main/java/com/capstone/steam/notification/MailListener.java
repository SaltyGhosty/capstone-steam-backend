package com.capstone.steam.notification;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.capstone.steam.config.AsyncConfig;
import com.capstone.steam.notification.event.OrderPaidEvent;
import com.capstone.steam.notification.event.PaymentFailedEvent;
import com.capstone.steam.notification.event.UserRegisteredEvent;

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
        emailService.sendWelcome(event.email(), event.displayName());
    }

    @Async(AsyncConfig.MAIL_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPaid(OrderPaidEvent event) {
        emailService.sendPurchaseReceipt(event.email(), event.displayName(), event.orderId(), event.total(),
                event.gameTitles());
    }

    @Async(AsyncConfig.MAIL_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentFailed(PaymentFailedEvent event) {
        emailService.sendPaymentFailed(event.email(), event.displayName(), event.orderId(), event.total(),
                event.reason());
    }
}
