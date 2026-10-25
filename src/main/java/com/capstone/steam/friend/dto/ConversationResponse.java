package com.capstone.steam.friend.dto;

import java.time.Instant;

/**
 * Una conversazione della casella della chat. {@code friend} è false se non sono più amici (sola lettura);
 * {@code lastMessage} e {@code lastMessageAt} sono null se non hanno mai parlato.
 */
public record ConversationResponse(String username, String displayName, String avatarUrl, boolean friend,
        long unread, String lastMessage, Instant lastMessageAt) {
}
