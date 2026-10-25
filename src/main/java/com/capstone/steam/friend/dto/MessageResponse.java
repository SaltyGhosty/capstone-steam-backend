package com.capstone.steam.friend.dto;

import java.time.Instant;

import com.capstone.steam.friend.FriendMessage;

/** Un messaggio visto da chi lo richiede: {@code mine} dice se lo ha scritto lui. */
public record MessageResponse(Long id, boolean mine, String body, Instant createdAt) {

    public static MessageResponse from(FriendMessage message, Long viewerId) {
        return new MessageResponse(message.getId(), message.getSender().getId().equals(viewerId), message.getBody(),
                message.getCreatedAt());
    }
}
