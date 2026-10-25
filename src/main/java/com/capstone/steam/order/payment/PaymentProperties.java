package com.capstone.steam.order.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param failureRate    probabilità (0.0-1.0) di rifiuto casuale, per le demo; 0 nei test
 * @param declinedSuffix una carta che termina così viene sempre rifiutata (demo deterministica)
 */
@ConfigurationProperties("app.payments")
public record PaymentProperties(double failureRate, String declinedSuffix) {
}
