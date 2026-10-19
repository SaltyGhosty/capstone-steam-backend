package com.capstone.steam.friend.dto;

import java.time.Instant;
import java.util.List;

/** {@code library} y {@code friends} son null si {@code visible} es false (perfil privado y no son amigos). */
public record PublicProfileResponse(
        UserCard user,
        Instant memberSince,
        boolean profilePublic,
        Relation relation,
        Long requestId,
        boolean visible,
        List<ProfileGame> library,
        List<FriendResponse> friends) {
}
