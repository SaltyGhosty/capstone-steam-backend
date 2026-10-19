package com.capstone.steam.friend.dto;

import java.time.Instant;

import com.capstone.steam.friend.Friendship;

/** {@code since} es el momento en que se aceptó la amistad. */
public record FriendResponse(UserCard user, Instant since) {

    /** @param viewerId el usuario cuya lista de amigos se muestra; {@code user} es el otro */
    public static FriendResponse from(Friendship friendship, Long viewerId) {
        return new FriendResponse(UserCard.from(friendship.other(viewerId)), friendship.getRespondedAt());
    }
}
