package com.capstone.steam.recommendation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Un messaggio della conversazione. Il testo dell'utente viaggia solo con {@code role = user}. */
public record ChatMessage(@NotNull Role role, @NotBlank @Size(max = 500) String content) {

    /** In minuscolo, così com'è nel JSON (e nel formato di OpenRouter). */
    public enum Role { user, assistant }
}
