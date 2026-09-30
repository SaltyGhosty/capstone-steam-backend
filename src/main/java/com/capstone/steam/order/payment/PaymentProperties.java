package com.capstone.steam.order.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param failureRate    probabilidad (0.0-1.0) de rechazo aleatorio, para demos; 0 en tests
 * @param declinedSuffix una tarjeta que termina así siempre se rechaza (demo determinista)
 */
@ConfigurationProperties("app.payments")
public record PaymentProperties(double failureRate, String declinedSuffix) {
}
