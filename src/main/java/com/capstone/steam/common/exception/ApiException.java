package com.capstone.steam.common.exception;

import org.springframework.http.HttpStatus;

/** Excepción de dominio que se traduce a un ProblemDetail con su status HTTP. */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;

    protected ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
