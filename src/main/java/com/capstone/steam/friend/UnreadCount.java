package com.capstone.steam.friend;

import java.time.Instant;

/** Messaggi non letti di un mittente: quanti e quando è arrivato l'ultimo. */
public record UnreadCount(Long senderId, long count, Instant lastAt) {
}
