package com.capstone.steam.common.exception;

import org.springframework.http.HttpStatus;

import com.capstone.steam.common.i18n.Messages;

/**
 * Excepción de dominio que se traduce a un ProblemDetail con su status HTTP.
 * Lleva la clave del texto (messages*.properties) y sus argumentos: el handler la traduce
 * al idioma de la petición. {@link #getMessage()} devuelve el texto en español (logs y tests).
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final transient Object[] args;

    protected ApiException(HttpStatus status, String code, Object... args) {
        super(Messages.get(Messages.DEFAULT, code, args));
        this.status = status;
        this.code = code;
        this.args = args;
    }

    public HttpStatus getStatus() {
        return status;
    }

    /** El texto en el idioma de la petición actual. */
    @Override
    public String getLocalizedMessage() {
        return Messages.get(code, args);
    }
}
