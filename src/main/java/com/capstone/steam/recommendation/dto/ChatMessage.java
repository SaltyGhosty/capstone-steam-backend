package com.capstone.steam.recommendation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Un mensaje de la conversación. El texto del usuario solo viaja con {@code role = user}. */
public record ChatMessage(@NotNull Role role, @NotBlank @Size(max = 500) String content) {

    /** En minúscula, tal cual en el JSON (y en el formato de OpenRouter). */
    public enum Role { user, assistant }
}
