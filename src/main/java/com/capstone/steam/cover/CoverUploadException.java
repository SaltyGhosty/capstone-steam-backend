package com.capstone.steam.cover;

import org.springframework.http.HttpStatus;

import com.capstone.steam.common.exception.ApiException;

/** Cloudinary non risponde o restituisce qualcosa che non si capisce (502). */
public class CoverUploadException extends ApiException {

    public CoverUploadException(Throwable cause) {
        super(HttpStatus.BAD_GATEWAY, "cover.uploadFailed");
        if (cause != null) {
            initCause(cause);
        }
    }
}
