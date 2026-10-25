package com.capstone.steam.common.exception;

import org.springframework.http.HttpStatus;

public class ConflictException extends ApiException {

    /** @param code chiave di messages*.properties */
    public ConflictException(String code, Object... args) {
        super(HttpStatus.CONFLICT, code, args);
    }
}
