package com.capstone.steam.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class TokenHashingTest {

    @Test
    void rawTokenHas43Base64UrlCharactersAndNeverRepeats() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            String token = TokenHashing.newRawToken();
            assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]{43}");
            assertThat(seen.add(token)).isTrue();
        }
    }

    @Test
    void sha256HexMatchesTheKnownVector() {
        assertThat(TokenHashing.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
