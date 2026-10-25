package com.capstone.steam.order.payment;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.random.RandomGenerator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.capstone.steam.order.PaymentMethod;

/**
 * Pagamenti simulati, senza provider reale:
 * <ul>
 *   <li>una carta che termina con {@code declinedSuffix} (per default 0000) viene sempre rifiutata;</li>
 *   <li>inoltre viene rifiutata a caso con probabilità {@code failureRate}.</li>
 * </ul>
 */
@Component
public class SimulatedPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(SimulatedPaymentGateway.class);

    private final PaymentProperties properties;
    private final RandomGenerator random;

    @Autowired // hay dos constructores: el otro solo lo usan los tests para fijar el "azar"
    public SimulatedPaymentGateway(PaymentProperties properties) {
        this(properties, RandomGenerator.getDefault());
    }

    SimulatedPaymentGateway(PaymentProperties properties, RandomGenerator random) {
        this.properties = properties;
        this.random = random;
    }

    @Override
    public String newReference() {
        return "SIM-" + UUID.randomUUID();
    }

    @Override
    public PaymentResult charge(String reference, BigDecimal amount, PaymentMethod method, String cardNumber) {
        if (method == PaymentMethod.CREDIT_CARD && cardNumber != null
                && properties.declinedSuffix() != null && cardNumber.endsWith(properties.declinedSuffix())) {
            log.info("Pago {} rechazado (tarjeta de prueba)", reference);
            return PaymentResult.declined("payment.declined.card");
        }
        if (random.nextDouble() < properties.failureRate()) {
            log.info("Pago {} rechazado (fallo simulado)", reference);
            return PaymentResult.declined("payment.declined.provider");
        }
        log.info("Pago {} aprobado: {} con {}", reference, amount, method);
        return PaymentResult.ok();
    }
}
