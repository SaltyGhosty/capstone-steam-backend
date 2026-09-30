package com.capstone.steam.order.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.Test;

import com.capstone.steam.order.PaymentMethod;

class SimulatedPaymentGatewayTest {

    private static final BigDecimal AMOUNT = new BigDecimal("19.99");

    /** {@code roll} es el valor "aleatorio" fijo que devolverá nextDouble(): test determinista. */
    private static SimulatedPaymentGateway gateway(double failureRate, double roll) {
        RandomGenerator fixed = new RandomGenerator() {
            @Override public long nextLong() { return 0; }
            @Override public double nextDouble() { return roll; }
        };
        return new SimulatedPaymentGateway(new PaymentProperties(failureRate, "0000"), fixed);
    }

    @Test
    void approvesValidCardWhenNoRandomFailure() {
        var result = gateway(0.0, 0.5).charge("ref", AMOUNT, PaymentMethod.CREDIT_CARD, "4242424242424242");
        assertThat(result.approved()).isTrue();
        assertThat(result.failureReason()).isNull();
    }

    @Test
    void declinesTestCardEndingInDeclinedSuffix() {
        var result = gateway(0.0, 0.5).charge("ref", AMOUNT, PaymentMethod.CREDIT_CARD, "4000000000000000");
        assertThat(result.approved()).isFalse();
        assertThat(result.failureReason()).contains("rechazada");
    }

    @Test
    void randomFailureRateAppliesToAnyMethod() {
        assertThat(gateway(0.3, 0.1).charge("ref", AMOUNT, PaymentMethod.PAYPAL, null).approved()).isFalse();
        assertThat(gateway(0.3, 0.9).charge("ref", AMOUNT, PaymentMethod.PAYPAL, null).approved()).isTrue();
    }

    @Test
    void referencesAreUnique() {
        var gateway = gateway(0.0, 0.5);
        assertThat(gateway.newReference()).startsWith("SIM-").isNotEqualTo(gateway.newReference());
    }
}
