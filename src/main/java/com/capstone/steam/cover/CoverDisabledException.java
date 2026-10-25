package com.capstone.steam.cover;

import org.springframework.http.HttpStatus;

import com.capstone.steam.common.exception.ApiException;

/** Il server non ha le credenziali di Cloudinary: il caricamento delle copertine è disattivato (503). */
public class CoverDisabledException extends ApiException {

    public CoverDisabledException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, "cover.disabled");
    }
}
