package com.capstone.steam.common.exception;

import org.springframework.http.HttpStatus;

public class UnauthorizedException extends ApiException {

    /** @param code clave de messages*.properties */
    public UnauthorizedException(String code, Object... args) {
        super(HttpStatus.UNAUTHORIZED, code, args);
    }
}
