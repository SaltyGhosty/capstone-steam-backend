package com.capstone.steam.game.search;

import java.util.Arrays;
import java.util.stream.Collectors;

import com.capstone.steam.common.exception.BadRequestException;

/** Ordinamenti del catalogo; il valore della query string è {@code param}. */
public enum GameSort {
    NEWEST("newest"),
    PRICE_ASC("price_asc"),
    PRICE_DESC("price_desc"),
    TOP_RATED("top_rated"),
    BEST_SELLING("best_selling"),
    TITLE("title");

    private final String param;

    GameSort(String param) {
        this.param = param;
    }

    public static GameSort fromParam(String value) {
        return Arrays.stream(values())
                .filter(s -> s.param.equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("catalog.invalidSort", value,
                        Arrays.stream(values()).map(s -> s.param).collect(Collectors.joining(", "))));
    }
}
