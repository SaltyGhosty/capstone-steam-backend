package com.capstone.steam.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code gameId} solo se usa al crear; en el PUT se ignora. */
public record ReviewRequest(
        Long gameId,
        @NotNull @Min(1) @Max(5) Integer rating,
        @NotNull Boolean recommended,
        @Size(max = 5000) String body) {
}
