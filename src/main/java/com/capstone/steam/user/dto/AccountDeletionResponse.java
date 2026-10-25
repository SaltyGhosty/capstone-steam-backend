package com.capstone.steam.user.dto;

import java.time.Instant;

/** Quando verrà eliminato l'account se non viene recuperato prima. */
public record AccountDeletionResponse(Instant deletionScheduledAt) {
}
