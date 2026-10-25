package com.capstone.steam.common.exception;

import org.springframework.http.HttpStatus;

import com.capstone.steam.common.i18n.Messages;

/**
 * Eccezione di dominio che si traduce in un ProblemDetail con il suo status HTTP.
 * Porta la chiave del testo (messages*.properties) e i suoi argomenti: l'handler la traduce
 * nella lingua della richiesta. {@link #getMessage()} restituisce il testo in spagnolo (log e test).
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

    /** Chiave del testo (messages*.properties); il frontend la usa per distinguere errori con lo stesso status. */
    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    /** Il testo nella lingua della richiesta corrente. */
    @Override
    public String getLocalizedMessage() {
        return Messages.get(code, args);
    }
}
