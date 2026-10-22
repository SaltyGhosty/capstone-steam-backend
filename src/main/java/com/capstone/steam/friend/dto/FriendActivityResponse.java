package com.capstone.steam.friend.dto;

import java.time.Instant;
import java.util.List;

/** Lo pendiente de quien consulta: solicitudes recibidas y mensajes sin leer; {@code total} es lo que suma el número de «Amigos». */
public record FriendActivityResponse(List<IncomingRequest> incomingRequests, List<UnreadFrom> unread, int total) {

    public record IncomingRequest(Long id, String username, String displayName, Instant createdAt) {
    }

    public record UnreadFrom(String username, String displayName, long count, Instant lastMessageAt) {
    }
}
