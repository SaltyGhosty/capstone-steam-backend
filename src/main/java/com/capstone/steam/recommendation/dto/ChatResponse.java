package com.capstone.steam.recommendation.dto;

import java.util.List;

import com.capstone.steam.game.dto.GameSummaryResponse;

/** Il testo dell'assistente e fino a 4 giochi del catalogo. */
public record ChatResponse(String reply, List<GameSummaryResponse> games) {
}
