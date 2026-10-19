package com.capstone.steam.recommendation.dto;

import java.util.List;

import com.capstone.steam.game.dto.GameSummaryResponse;

/** El texto del asistente y hasta 4 juegos del catálogo. */
public record ChatResponse(String reply, List<GameSummaryResponse> games) {
}
