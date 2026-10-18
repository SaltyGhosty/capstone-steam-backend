package com.capstone.steam.steamstore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.steam.notification.event.GameOnSaleEvent;
import com.jayway.jsonpath.JsonPath;

/** Sincronización de precios, avisos a la wishlist e importación, con Steam simulado. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@RecordApplicationEvents
class SteamIntegrationTest {

    private static final int CELESTE = 504230;

    @Autowired private MockMvc mvc;
    @Autowired private ApplicationEvents events;
    @MockitoBean private SteamStoreClient steam;

    @Test
    void syncUpdatesPricesAndWarnsWishlistsOnlyWhenASaleStarts() throws Exception {
        String dev = register("maddy_sync", "DEVELOPER");
        long gameId = createPublishedGame(dev, "Celeste Sync", CELESTE);
        String player = register("fan_sync", "PLAYER");
        mvc.perform(put("/api/wishlist/" + gameId).header(HttpHeaders.AUTHORIZATION, "Bearer " + player))
                .andExpect(status().is2xxSuccessful());
        when(steam.prices(anyCollection())).thenReturn(Map.of(CELESTE,
                new SteamPrice("EUR", new BigDecimal("18.99"), new BigDecimal("4.74"), 75)));
        String admin = login("admin", "Admin12345!");

        mvc.perform(post("/api/admin/steam/sync").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checked").value(1))
                .andExpect(jsonPath("$.updated").value(1))
                .andExpect(jsonPath("$.newSales").value(1))
                .andExpect(jsonPath("$.notified").value(1));

        mvc.perform(get("/api/games/celeste-sync"))
                .andExpect(jsonPath("$.price").value(18.99))
                .andExpect(jsonPath("$.discountPercent").value(75))
                .andExpect(jsonPath("$.priceSyncedAt").isNotEmpty());
        List<GameOnSaleEvent> sales = events.stream(GameOnSaleEvent.class).toList();
        assertThat(sales).hasSize(1);
        assertThat(sales.getFirst().recipients()).singleElement()
                .satisfies(r -> assertThat(r.email()).isEqualTo("fan_sync@test.com"));

        // la misma oferta otra vez: no hay aviso nuevo
        mvc.perform(post("/api/admin/steam/sync").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(jsonPath("$.updated").value(0))
                .andExpect(jsonPath("$.newSales").value(0));
        assertThat(events.stream(GameOnSaleEvent.class)).hasSize(1);
    }

    @Test
    void onlyAdminsCanSync() throws Exception {
        String player = register("curious_sync", "PLAYER");
        mvc.perform(post("/api/admin/steam/sync").header(HttpHeaders.AUTHORIZATION, "Bearer " + player))
                .andExpect(status().isForbidden());
    }

    @Test
    void importCreatesADraftWithTheSteamDataInTheThreeLanguages() throws Exception {
        String dev = register("cherry_import", "DEVELOPER");
        SteamPrice price = new SteamPrice("EUR", new BigDecimal("14.79"), new BigDecimal("10.35"), 30);
        when(steam.app(eq(367520), eq("en"))).thenReturn(Optional.of(new SteamApp(367520, "Hollow Knight",
                "Forge your own path", LocalDate.of(2017, 2, 24), List.of("1", "23", "999"), false, price)));
        when(steam.app(eq(367520), eq("es"))).thenReturn(Optional.of(new SteamApp(367520, "Hollow Knight",
                "Forja tu propio camino", null, List.of(), false, price)));
        when(steam.app(eq(367520), eq("it"))).thenReturn(Optional.of(new SteamApp(367520, "Hollow Knight",
                "Forgia la tua strada", null, List.of(), false, price)));

        mvc.perform(post("/api/developer/games/import").header(HttpHeaders.AUTHORIZATION, "Bearer " + dev)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"steamAppId\":367520}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Hollow Knight"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.steamAppId").value(367520))
                .andExpect(jsonPath("$.descriptions.es").value("Forja tu propio camino"))
                .andExpect(jsonPath("$.descriptions.en").value("Forge your own path"))
                .andExpect(jsonPath("$.descriptions.it").value("Forgia la tua strada"))
                .andExpect(jsonPath("$.releaseDate").value("2017-02-24"))
                .andExpect(jsonPath("$.price").value(14.79))
                .andExpect(jsonPath("$.discountPercent").value(30))
                .andExpect(jsonPath("$.genres.length()").value(2))
                .andExpect(jsonPath("$.coverUrl").value(
                        "https://cdn.cloudflare.steamstatic.com/steam/apps/367520/library_600x900_2x.jpg"));

        mvc.perform(post("/api/developer/games/import").header(HttpHeaders.AUTHORIZATION, "Bearer " + dev)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"steamAppId\":367520}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("A game is already linked to Steam AppID 367520"));
    }

    @Test
    void importOfAnUnknownAppIs404() throws Exception {
        String dev = register("ghost_import", "DEVELOPER");
        when(steam.app(eq(1), eq("en"))).thenReturn(Optional.empty());
        mvc.perform(post("/api/developer/games/import").header(HttpHeaders.AUTHORIZATION, "Bearer " + dev)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"steamAppId\":1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Steam no tiene ningún juego con el AppID 1"));
    }

    // ------------------------------------------------------------------

    private long createPublishedGame(String token, String title, int appId) throws Exception {
        Integer genreId = JsonPath.read(mvc.perform(get("/api/genres")).andReturn().getResponse().getContentAsString(),
                "$[0].id");
        String body = mvc.perform(post("/api/developer/games").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                        {"title":"%s","price":19.99,"steamAppId":%d,"genreIds":[%d]}""".formatted(title, appId, genreId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.id")).longValue();
        mvc.perform(post("/api/developer/games/" + id + "/publish").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        return id;
    }

    private String register(String username, String type) throws Exception {
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username":"%s","email":"%s@test.com","password":"password123","accountType":"%s"}"""
                        .formatted(username, username, type)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private String login(String login, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"%s\",\"password\":\"%s\"}".formatted(login, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }
}
