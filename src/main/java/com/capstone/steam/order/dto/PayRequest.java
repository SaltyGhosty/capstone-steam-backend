package com.capstone.steam.order.dto;

import com.capstone.steam.order.PaymentMethod;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * {@code cardNumber}: obbligatorio con CREDIT_CARD; solo cifre. È simulato e NON viene salvato.
 * Termina in 0000 -> rifiuto (per provare il flusso di pagamento fallito).
 */
public record PayRequest(
        @NotNull PaymentMethod method,
        @Pattern(regexp = "^\\d{12,19}$", message = "{validation.cardNumber}") String cardNumber) {
}
