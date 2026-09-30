package com.capstone.steam.game.dto;

import com.capstone.steam.game.GameStatus;

import jakarta.validation.constraints.NotNull;

public record StatusRequest(@NotNull GameStatus status) {
}
