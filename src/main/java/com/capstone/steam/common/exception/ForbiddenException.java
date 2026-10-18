package com.capstone.steam.common.exception;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends ApiException {

    /** @param code clave de messages*.properties */
    public ForbiddenException(String code, Object... args) {
        super(HttpStatus.FORBIDDEN, code, args);
    }
}
