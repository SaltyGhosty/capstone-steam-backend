package com.capstone.steam.common.exception;

import org.springframework.http.HttpStatus;

public class BadRequestException extends ApiException {

    /** @param code chiave di messages*.properties */
    public BadRequestException(String code, Object... args) {
        super(HttpStatus.BAD_REQUEST, code, args);
    }
}
