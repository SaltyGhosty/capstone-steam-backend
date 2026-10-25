package com.capstone.steam.friend.dto;

import java.time.Instant;
import java.util.List;

/** {@code library} e {@code friends} sono null se {@code visible} è false (profilo privato e non sono amici). */
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
