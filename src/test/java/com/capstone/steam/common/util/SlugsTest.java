package com.capstone.steam.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SlugsTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Hollow Knight              | hollow-knight",
            "Pokémon: Edición Oro!      | pokemon-edicion-oro",
            "  --Half-Life 2--          | half-life-2",
            "100% Orange Juice          | 100-orange-juice",
            "¿¡!?                       | item"
    })
    void slugify(String input, String expected) {
        assertThat(Slugs.slugify(input)).isEqualTo(expected);
    }
}
