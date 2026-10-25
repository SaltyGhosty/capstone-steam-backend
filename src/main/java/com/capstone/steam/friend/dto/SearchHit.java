package com.capstone.steam.friend.dto;

/** {@code requestId} arriva solo se c'è una richiesta pendente tra i due. */
public record SearchHit(UserCard user, Relation relation, Long requestId) {
}
