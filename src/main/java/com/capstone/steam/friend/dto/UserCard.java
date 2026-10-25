package com.capstone.steam.friend.dto;

import com.capstone.steam.user.User;

/** Il minimo pubblico di un giocatore: mai l'email. */
public record UserCard(String username, String displayName, String avatarUrl) {

    public static UserCard from(User user) {
        return new UserCard(user.getUsername(), user.getDisplayName(), user.getAvatarUrl());
    }
}
