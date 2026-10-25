package com.capstone.steam.recommendation.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Gli ultimi messaggi della conversazione (massimo 10); l'ultimo deve essere dell'utente. */
public record ChatRequest(@NotEmpty @Size(max = 10) List<@NotNull @Valid ChatMessage> messages) {
}
