package com.capstone.steam.common.exception;

import org.springframework.http.HttpStatus;

public class TooManyRequestsException extends ApiException {

    /** @param code clave de messages*.properties */
    public TooManyRequestsException(String code, Object... args) {
        super(HttpStatus.TOO_MANY_REQUESTS, code, args);
    }
}
