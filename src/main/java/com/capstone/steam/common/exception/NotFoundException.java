package com.capstone.steam.common.exception;

import org.springframework.http.HttpStatus;

public class NotFoundException extends ApiException {

    /** @param code clave de messages*.properties */
    public NotFoundException(String code, Object... args) {
        super(HttpStatus.NOT_FOUND, code, args);
    }

    /** @param resource user, game, genre, order o review (clave notFound.{resource}) */
    public static NotFoundException of(String resource, Object id) {
        return new NotFoundException("notFound." + resource, String.valueOf(id));
    }
}
