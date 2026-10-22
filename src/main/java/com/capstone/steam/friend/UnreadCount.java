package com.capstone.steam.friend;

import java.time.Instant;

/** Mensajes sin leer de un remitente: cuántos y cuándo llegó el último. */
public record UnreadCount(Long senderId, long count, Instant lastAt) {
}
