package com.capstone.steam.cover;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class ImageTypeTest {

    @Test
    void recognisesJpegPngAndWebpByTheirMagicBytes() {
        assertThat(ImageType.detect(bytes(0xFF, 0xD8, 0xFF, 0xE0, 0, 0))).contains(ImageType.JPEG);
        assertThat(ImageType.detect(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0))).contains(ImageType.PNG);
        assertThat(ImageType.detect(webp("WEBP"))).contains(ImageType.WEBP);
    }

    @Test
    void rejectsGifTextEmptyAndRiffWithoutWebp() {
        assertThat(ImageType.detect(bytes(0x47, 0x49, 0x46, 0x38, 0x39, 0x61))).isEqualTo(Optional.empty());
        assertThat(ImageType.detect("hola, no soy una imagen".getBytes(StandardCharsets.UTF_8))).isEmpty();
        assertThat(ImageType.detect(new byte[0])).isEmpty();
        assertThat(ImageType.detect(webp("WAVE"))).isEmpty();
    }

    @Test
    void rejectsTruncatedHeaders() {
        assertThat(ImageType.detect(bytes(0x89, 0x50, 0x4E))).isEmpty();
        assertThat(ImageType.detect(bytes(0xFF, 0xD8))).isEmpty();
    }

    private static byte[] webp(String format) {
        return ("RIFF" + "\0\0\0\0" + format).getBytes(StandardCharsets.ISO_8859_1);
    }

    private static byte[] bytes(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = (byte) values[i];
        }
        return out;
    }
}
