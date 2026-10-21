package com.capstone.steam.user.dto;

import jakarta.validation.constraints.NotBlank;

public record DeletionRequest(@NotBlank String password) {
}
