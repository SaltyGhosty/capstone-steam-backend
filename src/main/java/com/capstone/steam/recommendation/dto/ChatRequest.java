package com.capstone.steam.recommendation.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/** Los últimos mensajes de la conversación (máximo 10); el último tiene que ser del usuario. */
public record ChatRequest(@NotEmpty @Size(max = 10) List<@Valid ChatMessage> messages) {
}
