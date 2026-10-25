package com.capstone.steam.friend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Il testo si salva ripulito: gli spazi agli estremi non contano per il massimo. */
public record SendMessageRequest(@NotBlank @Size(max = 1000) String body) {

    public SendMessageRequest {
        body = body == null ? null : body.strip();
    }
}
