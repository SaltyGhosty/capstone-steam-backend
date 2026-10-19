package com.capstone.steam.cover;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.capstone.steam.common.exception.BadRequestException;

class CloudinaryCoverStorageTest {

    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochSecond(1_700_000_000L), ZoneOffset.UTC);

    private static CloudinaryProperties props(String url) {
        return new CloudinaryProperties(url, "arcade/covers", Duration.ofSeconds(5));
    }

    @Test
    void parsesTheCloudinaryUrl() {
        CloudinaryCoverStorage storage = new CloudinaryCoverStorage(props("cloudinary://key:secret@demo"),
                RestClient.builder().build(), CLOCK);
        assertThat(storage.enabled()).isTrue();
        assertThat(storage.cloudName()).isEqualTo("demo");
        assertThat(storage.apiKey()).isEqualTo("key");
    }

    @Test
    void anEmptyOrMalformedUrlDisablesTheUpload() {
        assertThat(new CloudinaryCoverStorage(props(""), RestClient.builder().build(), CLOCK).enabled()).isFalse();
        assertThat(new CloudinaryCoverStorage(props(null), RestClient.builder().build(), CLOCK).enabled()).isFalse();
        assertThat(new CloudinaryCoverStorage(props("basura"), RestClient.builder().build(), CLOCK).enabled()).isFalse();
    }

    @Test
    void signsTheParametersInAlphabeticalOrderWithSha1() throws Exception {
        Map<String, String> params = Map.of("timestamp", "1700000000", "public_id", "game-7",
                "folder", "arcade/covers", "overwrite", "true");
        String expected = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(
                "folder=arcade/covers&overwrite=true&public_id=game-7&timestamp=1700000000abc"
                        .getBytes(StandardCharsets.UTF_8)));
        assertThat(CloudinaryCoverStorage.signature(params, "abc")).isEqualTo(expected);
    }

    @Test
    void uploadReturnsTheSecureUrl() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.cloudinary.com/v1_1/demo/image/upload"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"secure_url\":\"https://res.cloudinary.com/demo/image/upload/arcade/covers/game-7.png\"}",
                        MediaType.APPLICATION_JSON));
        CloudinaryCoverStorage storage = new CloudinaryCoverStorage(props("cloudinary://key:secret@demo"),
                builder.build(), CLOCK);

        String url = storage.upload(7, new byte[] {1, 2, 3}, ImageType.PNG);

        assertThat(url).isEqualTo("https://res.cloudinary.com/demo/image/upload/arcade/covers/game-7.png");
        server.verify();
    }

    @Test
    void anHttpUrlOrAnErrorIsAnUploadFailureThatNeverLeaksTheSecret() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.cloudinary.com/v1_1/demo/image/upload"))
                .andRespond(withSuccess("{\"secure_url\":\"http://res.cloudinary.com/x.png\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.cloudinary.com/v1_1/demo/image/upload"))
                .andRespond(withServerError());
        CloudinaryCoverStorage storage = new CloudinaryCoverStorage(props("cloudinary://key:topsecret@demo"),
                builder.build(), CLOCK);

        assertThatThrownBy(() -> storage.upload(7, new byte[] {1}, ImageType.PNG))
                .isInstanceOf(CoverUploadException.class);
        assertThatThrownBy(() -> storage.upload(7, new byte[] {1}, ImageType.PNG))
                .isInstanceOf(CoverUploadException.class)
                .satisfies(e -> assertThat(String.valueOf(e.getCause())).doesNotContain("topsecret"));
    }

    @Test
    void aCloudinary400IsABadRequestAndA500IsStillABadGateway() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.cloudinary.com/v1_1/demo/image/upload"))
                .andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"Invalid image file\"}}"));
        server.expect(requestTo("https://api.cloudinary.com/v1_1/demo/image/upload"))
                .andRespond(withServerError());
        CloudinaryCoverStorage storage = new CloudinaryCoverStorage(props("cloudinary://key:secret@demo"),
                builder.build(), CLOCK);

        assertThatThrownBy(() -> storage.upload(7, new byte[] {1}, ImageType.PNG))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("La portada tiene que ser una imagen JPEG, PNG o WebP");
        assertThatThrownBy(() -> storage.upload(7, new byte[] {1}, ImageType.PNG))
                .isInstanceOf(CoverUploadException.class);
    }
}
