package com.capstone.steam.user.dto;

import java.time.Instant;

/** Cuándo se eliminará la cuenta si no se recupera antes. */
public record AccountDeletionResponse(Instant deletionScheduledAt) {
}
