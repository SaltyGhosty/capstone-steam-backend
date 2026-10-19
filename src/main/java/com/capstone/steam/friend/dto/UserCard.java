package com.capstone.steam.friend.dto;

import com.capstone.steam.user.User;

/** Lo mínimo público de un jugador: nunca el email. */
public record UserCard(String username, String displayName, String avatarUrl) {

    public static UserCard from(User user) {
        return new UserCard(user.getUsername(), user.getDisplayName(), user.getAvatarUrl());
    }
}
