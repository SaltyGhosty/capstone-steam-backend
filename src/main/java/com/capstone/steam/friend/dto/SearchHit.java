package com.capstone.steam.friend.dto;

/** {@code requestId} solo viene si hay una solicitud pendiente entre los dos. */
public record SearchHit(UserCard user, Relation relation, Long requestId) {
}
