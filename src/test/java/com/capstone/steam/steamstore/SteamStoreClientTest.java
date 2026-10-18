package com.capstone.steam.steamstore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.json.JsonMapper;

/** Respuestas reales de Steam (recortadas) contra un servidor simulado. */
class SteamStoreClientTest {

    private MockRestServiceServer server;
    private SteamStoreClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://store.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new SteamStoreClient(builder.build(), JsonMapper.builder().build(),
                new SteamProperties(false, "-", "es", "https://store.test", Duration.ofSeconds(5)));
    }

    @Test
    void pricesSkipsUnknownAndFreeGames() {
        server.expect(requestTo("https://store.test/api/appdetails?appids=504230%2C252950%2C1&cc=es&filters=price_overview"))
                .andRespond(withSuccess("""
                        {"504230":{"success":true,"data":{"price_overview":{"currency":"EUR","initial":1899,"final":474,"discount_percent":75}}},
                         "252950":{"success":true,"data":[]},
                         "1":{"success":false}}""", MediaType.APPLICATION_JSON));

        Map<Integer, SteamPrice> prices = client.prices(List.of(504230, 252950, 1));

        assertThat(prices).containsOnlyKeys(504230);
        assertThat(prices.get(504230)).isEqualTo(
                new SteamPrice("EUR", new BigDecimal("18.99"), new BigDecimal("4.74"), 75));
        server.verify();
    }

    @Test
    void appReadsTheLocalizedDescriptionAsPlainText() {
        server.expect(requestTo("https://store.test/api/appdetails?appids=367520&cc=es&l=italian"))
                .andRespond(withSuccess("""
                        {"367520":{"success":true,"data":{"name":"Hollow Knight","is_free":false,
                          "short_description":"Forgia la tua strada &quot;epica&quot; <b>in</b> Hallownest &amp; oltre",
                          "release_date":{"date":"24 feb 2017"},
                          "genres":[{"id":"1","description":"Azione"},{"id":"23","description":"Indie"}],
                          "price_overview":{"currency":"EUR","initial":1479,"final":1479,"discount_percent":0}}}}""",
                        MediaType.APPLICATION_JSON));

        SteamApp app = client.app(367520, "it").orElseThrow();

        assertThat(app.name()).isEqualTo("Hollow Knight");
        assertThat(app.description()).isEqualTo("Forgia la tua strada \"epica\" in Hallownest & oltre");
        assertThat(app.genreIds()).containsExactly("1", "23");
        assertThat(app.price().initial()).isEqualByComparingTo("14.79");
        assertThat(app.releaseDate()).isNull(); // la fecha solo se entiende en la ficha en inglés
    }

    @Test
    void parsesEnglishReleaseDatesAndIgnoresTheRest() {
        assertThat(SteamStoreClient.releaseDate("24 Feb, 2017")).isEqualTo(LocalDate.of(2017, 2, 24));
        assertThat(SteamStoreClient.releaseDate("Feb 24, 2017")).isEqualTo(LocalDate.of(2017, 2, 24));
        assertThat(SteamStoreClient.releaseDate("Coming soon")).isNull();
    }

    @Test
    void unknownAppIsEmptyAndServerErrorsAreSteamUnavailable() {
        server.expect(requestTo("https://store.test/api/appdetails?appids=9&cc=es&l=english"))
                .andRespond(withSuccess("{\"9\":{\"success\":false}}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://store.test/api/appdetails?appids=10&cc=es&l=spanish"))
                .andRespond(withServerError());

        assertThat(client.app(9, "en")).isEmpty();
        assertThatThrownBy(() -> client.app(10, "es")).isInstanceOf(SteamUnavailableException.class)
                .hasMessage("No se ha podido conectar con la tienda de Steam; inténtalo más tarde");
    }
}
