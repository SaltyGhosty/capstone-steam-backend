package com.capstone.steam.order.dto;

import com.capstone.steam.order.PaymentMethod;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * {@code cardNumber}: obligatorio con CREDIT_CARD; solo dígitos. Es simulado y NO se guarda.
 * Termina en 0000 -> rechazo (para probar el flujo de pago fallido).
 */
public record PayRequest(
        @NotNull PaymentMethod method,
        @Pattern(regexp = "^\\d{12,19}$", message = "debe tener entre 12 y 19 dígitos") String cardNumber) {
}
