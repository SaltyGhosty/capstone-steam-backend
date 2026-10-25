package com.capstone.steam.common.exception;

import org.springframework.http.HttpStatus;

public class UnauthorizedException extends ApiException {

    /** @param code chiave di messages*.properties */
    public UnauthorizedException(String code, Object... args) {
        super(HttpStatus.UNAUTHORIZED, code, args);
    }
}
