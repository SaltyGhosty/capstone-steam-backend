package com.capstone.steam.steamstore;

import org.springframework.http.HttpStatus;

import com.capstone.steam.common.exception.ApiException;

/** Steam no responde o devuelve algo que no se entiende (502). */
public class SteamUnavailableException extends ApiException {

    public SteamUnavailableException(Throwable cause) {
        super(HttpStatus.BAD_GATEWAY, "steam.unavailable");
        initCause(cause);
    }
}
