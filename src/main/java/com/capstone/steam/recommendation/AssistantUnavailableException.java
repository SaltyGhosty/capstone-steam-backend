package com.capstone.steam.recommendation;

import org.springframework.http.HttpStatus;

import com.capstone.steam.common.exception.ApiException;

/** L'assistente non può rispondere: senza chiave, senza rete, senza quota o con una risposta inutilizzabile (503). */
public class AssistantUnavailableException extends ApiException {

    public AssistantUnavailableException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, "assistant.unavailable");
    }

    public AssistantUnavailableException(Throwable cause) {
        this();
        initCause(cause);
    }
}
