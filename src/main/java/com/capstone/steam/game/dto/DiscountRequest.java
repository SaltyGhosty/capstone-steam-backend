package com.capstone.steam.game.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record DiscountRequest(@NotNull @Min(0) @Max(100) Integer percent) {
}
