package com.capstone.steam.genre.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GenreRequest(@NotBlank @Size(max = 50) String name) {
}
