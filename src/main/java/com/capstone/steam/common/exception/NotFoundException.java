package com.capstone.steam.common.exception;

import org.springframework.http.HttpStatus;

public class NotFoundException extends ApiException {

    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }

    public static NotFoundException of(String resource, Object id) {
        return new NotFoundException(resource + " no encontrado: " + id);
    }
}
