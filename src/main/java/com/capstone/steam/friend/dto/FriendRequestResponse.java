package com.capstone.steam.friend.dto;

import java.time.Instant;

import com.capstone.steam.friend.Friendship;
import com.capstone.steam.friend.FriendshipStatus;

/** {@code user} è l'altra persona, non chi consulta. */
public record FriendRequestResponse(Long id, UserCard user, FriendshipStatus status, Instant createdAt) {

    public static FriendRequestResponse from(Friendship friendship, Long viewerId) {
        return new FriendRequestResponse(friendship.getId(), UserCard.from(friendship.other(viewerId)),
                friendship.getStatus(), friendship.getCreatedAt());
    }
}
