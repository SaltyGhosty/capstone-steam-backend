package com.capstone.steam.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

class BrevoMailClientTest {

    private static final String URL = "https://api.brevo.com/v3/smtp/email";

    @Test
    void withoutApiKeyItIsDisabled() {
        assertThat(new BrevoMailClient(RestClient.builder().build(), "", URL).enabled()).isFalse();
        assertThat(new BrevoMailClient(RestClient.builder().build(), null, URL).enabled()).isFalse();
        assertThat(new BrevoMailClient(RestClient.builder().build(), " xkeysib-1 ", URL).enabled()).isTrue();
    }

    @Test
    void postsTheEmailAsJsonWithTheApiKeyHeader() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("api-key", "xkeysib-1"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"sender":{"name":"ARCADE","email":"tienda@gmail.com"},
                         "to":[{"email":"ana@gmail.com"}],
                         "subject":"Hola","htmlContent":"<p>¡Hola!</p>"}"""))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"messageId\":\"<1@smtp-relay.mailin.fr>\"}"));

        new BrevoMailClient(builder.build(), "xkeysib-1", URL)
                .send("tienda@gmail.com", "ARCADE", "ana@gmail.com", "Hola", "<p>¡Hola!</p>");

        server.verify();
    }

    @Test
    void aRejectedRequestThrowsAndIsNotRetryable() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON).body("{\"code\":\"invalid_parameter\"}"));

        BrevoMailClient client = new BrevoMailClient(builder.build(), "xkeysib-1", URL);

        assertThatThrownBy(() -> client.send("tienda@gmail.com", "ARCADE", "ana@gmail.com", "Hola", "<p>x</p>"))
                .isInstanceOf(HttpClientErrorException.BadRequest.class)
                .satisfies(ex -> assertThat(BrevoMailClient.isRetryable(ex)).isFalse());
    }

    @Test
    void networkErrorsRateLimitsAndServerErrorsAreRetryable() {
        assertThat(BrevoMailClient.isRetryable(new ResourceAccessException("timeout", new IOException()))).isTrue();
        assertThat(BrevoMailClient.isRetryable(
                HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "", null, null, null))).isTrue();
        assertThat(BrevoMailClient.isRetryable(
                HttpServerErrorException.create(HttpStatus.BAD_GATEWAY, "", null, null, null)))
                .isTrue();
        assertThat(BrevoMailClient.isRetryable(
                HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "", null, null, null))).isFalse();
        assertThat(BrevoMailClient.isRetryable(new IllegalStateException())).isFalse();
    }
}
