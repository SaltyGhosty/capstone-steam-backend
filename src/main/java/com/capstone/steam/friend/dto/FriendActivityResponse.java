package com.capstone.steam.friend.dto;

import java.time.Instant;
import java.util.List;

/** Le novità di chi consulta: richieste ricevute e messaggi non letti; {@code total} è ciò che somma il numero di «Amici». */
public record FriendActivityResponse(List<IncomingRequest> incomingRequests, List<UnreadFrom> unread, int total) {

    public record IncomingRequest(Long id, String username, String displayName, Instant createdAt) {
    }

    public record UnreadFrom(String username, String displayName, long count, Instant lastMessageAt) {
    }
}
