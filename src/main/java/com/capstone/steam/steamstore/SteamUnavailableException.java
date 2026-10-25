package com.capstone.steam.steamstore;

import org.springframework.http.HttpStatus;

import com.capstone.steam.common.exception.ApiException;

/** Steam non risponde o restituisce qualcosa che non si capisce (502). */
public class SteamUnavailableException extends ApiException {

    public SteamUnavailableException(Throwable cause) {
        super(HttpStatus.BAD_GATEWAY, "steam.unavailable");
        initCause(cause);
    }
}
