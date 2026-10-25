package com.capstone.steam.library.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Minuti di una sessione di gioco simulata (al massimo un giorno). */
public record PlayRequest(@NotNull @Min(1) @Max(1440) Integer minutes) {
}
