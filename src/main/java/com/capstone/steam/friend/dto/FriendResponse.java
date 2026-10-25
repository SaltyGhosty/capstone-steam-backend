package com.capstone.steam.friend.dto;

import java.time.Instant;

import com.capstone.steam.friend.Friendship;

/** {@code since} è il momento in cui è stata accettata l'amicizia; {@code unreadMessages}, ciò che quell'amico mi ha scritto e non ho letto. */
public record FriendResponse(UserCard user, Instant since, int unreadMessages) {

    /** @param viewerId l'utente di cui si mostra la lista amici; {@code user} è l'altro */
    public static FriendResponse from(Friendship friendship, Long viewerId, int unreadMessages) {
        return new FriendResponse(UserCard.from(friendship.other(viewerId)), friendship.getRespondedAt(),
                unreadMessages);
    }
}
