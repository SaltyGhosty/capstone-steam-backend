package com.capstone.steam.order.payment;

import java.math.BigDecimal;

import com.capstone.steam.order.PaymentMethod;

/**
 * Gateway di pagamento. Oggi esiste solo quello simulato; uno reale (Stripe...) implementerebbe questa
 * stessa interfaccia senza toccare il checkout.
 */
public interface PaymentGateway {

    /** Riferimento univoco della transazione presso il provider. */
    String newReference();

    /**
     * @param cardNumber solo per CREDIT_CARD; non viene mai persistito
     */
    PaymentResult charge(String reference, BigDecimal amount, PaymentMethod method, String cardNumber);

    record PaymentResult(boolean approved, String failureReason) {

        public static PaymentResult ok() {
            return new PaymentResult(true, null);
        }

        /** @param reason chiave di messages*.properties (payment.declined.*) */
        public static PaymentResult declined(String reason) {
            return new PaymentResult(false, reason);
        }
    }
}
