package com.capstone.steam.order.payment;

import java.math.BigDecimal;

import com.capstone.steam.order.PaymentMethod;

/**
 * Pasarela de pago. Hoy solo existe la simulada; una real (Stripe...) implementaría esta
 * misma interfaz sin tocar el checkout.
 */
public interface PaymentGateway {

    /** Referencia única de la transacción en el proveedor. */
    String newReference();

    /**
     * @param cardNumber solo para CREDIT_CARD; nunca se persiste
     */
    PaymentResult charge(String reference, BigDecimal amount, PaymentMethod method, String cardNumber);

    record PaymentResult(boolean approved, String failureReason) {

        public static PaymentResult ok() {
            return new PaymentResult(true, null);
        }

        public static PaymentResult declined(String reason) {
            return new PaymentResult(false, reason);
        }
    }
}
