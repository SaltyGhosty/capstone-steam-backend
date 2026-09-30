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
 * Pagos simulados, sin proveedor real:
 * <ul>
 *   <li>una tarjeta que termina en {@code declinedSuffix} (por defecto 0000) se rechaza siempre;</li>
 *   <li>además se rechaza al azar con probabilidad {@code failureRate}.</li>
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
            return PaymentResult.declined("Tarjeta rechazada por el emisor");
        }
        if (random.nextDouble() < properties.failureRate()) {
            log.info("Pago {} rechazado (fallo simulado)", reference);
            return PaymentResult.declined("El proveedor de pagos no ha podido procesar el cobro");
        }
        log.info("Pago {} aprobado: {} con {}", reference, amount, method);
        return PaymentResult.ok();
    }
}
