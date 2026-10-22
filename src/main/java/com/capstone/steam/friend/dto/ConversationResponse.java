package com.capstone.steam.friend.dto;

import java.time.Instant;

/**
 * Una conversación de la bandeja de chat. {@code friend} es falso si ya no son amigos (solo lectura);
 * {@code lastMessage} y {@code lastMessageAt} son null si nunca hablaron.
 */
public record ConversationResponse(String username, String displayName, String avatarUrl, boolean friend,
        long unread, String lastMessage, Instant lastMessageAt) {
}
