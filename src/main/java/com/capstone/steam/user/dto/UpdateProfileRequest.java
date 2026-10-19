package com.capstone.steam.user.dto;

import org.hibernate.validator.constraints.URL;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Campos null = no se modifican. */
public record UpdateProfileRequest(
        @Size(min = 1, max = 100) String displayName,
        @URL(regexp = "(?i)^https?://.+") @Size(max = 500) String avatarUrl,
        @Pattern(regexp = "^(es|en|it)$", message = "{validation.locale}") String locale) {
}
