package com.capstone.steam.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VerificationPreviewRequest(@NotBlank @Size(max = 200) String token) {
}
