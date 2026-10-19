package com.capstone.steam.cover;

import org.springframework.http.HttpStatus;

import com.capstone.steam.common.exception.ApiException;

/** El servidor no tiene credenciales de Cloudinary: la subida de portadas está desactivada (503). */
public class CoverDisabledException extends ApiException {

    public CoverDisabledException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, "cover.disabled");
    }
}
