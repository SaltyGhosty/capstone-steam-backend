package com.capstone.steam.recommendation;

import org.springframework.http.HttpStatus;

import com.capstone.steam.common.exception.ApiException;

/** El asistente no puede contestar: sin clave, sin red, sin cupo o con una respuesta inservible (503). */
public class AssistantUnavailableException extends ApiException {

    public AssistantUnavailableException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, "assistant.unavailable");
    }

    public AssistantUnavailableException(Throwable cause) {
        this();
        initCause(cause);
    }
}
