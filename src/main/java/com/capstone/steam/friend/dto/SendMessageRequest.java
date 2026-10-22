package com.capstone.steam.friend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** El texto se guarda recortado: los espacios de los extremos no cuentan para el máximo. */
public record SendMessageRequest(@NotBlank @Size(max = 1000) String body) {

    public SendMessageRequest {
        body = body == null ? null : body.strip();
    }
}
