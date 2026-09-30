package com.capstone.steam.library.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Minutos de una sesión de juego simulada (como mucho un día). */
public record PlayRequest(@NotNull @Min(1) @Max(1440) Integer minutes) {
}
