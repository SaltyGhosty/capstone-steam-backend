package com.capstone.steam.game;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.capstone.steam.support.MutableClock;
import com.capstone.steam.user.AccountAnonymizer;
import com.jayway.jsonpath.JsonPath;

/**
 * Ficha de un juego retirado: la ven quien lo tiene, su developer y el admin; el resto, 404. El estudio eliminado se
 * fabrica de verdad (baja + anonimizador, que confirma en su propia transacción), así que el test no es
 * {@code @Transactional}: usa su propia H2 y vacía las tablas antes de cada test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(MutableClock.Config.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:delisted_visibility;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "spring.mail.username="
})
class DelistedVisibilityIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-11-01T10:00:00Z");
    private static final String GOOD_CARD = "{\"method\":\"CREDIT_CARD\",\"cardNumber\":\"4242424242424242\"}";

    @Autowired private MockMvc mvc;
    @Autowired private MutableClock clock;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AccountAnonymizer anonymizer;

    private String devToken;
    private String ownerToken;
    private String otherToken;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        for (String table : List.of("payments", "library_entries", "order_items", "orders", "reviews", "cart_items",
                "wishlist_items", "friendships", "refresh_tokens", "email_verification_tokens", "game_genres",
                "games")) {
            jdbc.update("DELETE FROM " + table);
        }
        jdbc.update("DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE username <> 'admin')");
        jdbc.update("DELETE FROM users WHERE username <> 'admin'");
        clock.now = T0;
        devToken = register("estudio", "estudio@test.com", "DEVELOPER");
        ownerToken = register("dueno", "dueno@test.com", "PLAYER");
        otherToken = register("otro", "otro@test.com", "PLAYER");
        adminToken = login("admin", "Admin12345!");
    }

    @Test
    void ownerSeesDelistedGameAndItsReviews() throws Exception {
        int id = publishGame("Hades");
        buy(ownerToken, id);
        review(ownerToken, id).andExpect(status().isCreated());
        delist(id);

        mvc.perform(auth(get("/api/games/hades"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELISTED"))
                .andExpect(jsonPath("$.developer.displayName").value("estudio"))
                .andExpect(jsonPath("$.developer.deleted").value(false));
        mvc.perform(auth(get("/api/games/hades/reviews"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        // su developer y el admin también la ven
        mvc.perform(auth(get("/api/games/hades"), devToken)).andExpect(status().isOk());
        mvc.perform(auth(get("/api/games/hades"), adminToken)).andExpect(status().isOk());
    }

    @Test
    void othersGet404ForDelistedGame() throws Exception {
        int id = publishGame("Hades");
        buy(ownerToken, id);
        delist(id);

        mvc.perform(get("/api/games/hades")).andExpect(status().isNotFound());
        mvc.perform(auth(get("/api/games/hades"), otherToken)).andExpect(status().isNotFound());
        mvc.perform(get("/api/games/hades/reviews")).andExpect(status().isNotFound());
        mvc.perform(auth(get("/api/games/hades/reviews"), otherToken)).andExpect(status().isNotFound());
        // un borrador sigue siendo 404 para todos en los endpoints públicos
        createDraft("Borrador");
        mvc.perform(get("/api/games/borrador")).andExpect(status().isNotFound());
        mvc.perform(auth(get("/api/games/borrador"), ownerToken)).andExpect(status().isNotFound());
        mvc.perform(auth(get("/api/games/borrador"), adminToken)).andExpect(status().isNotFound());
        mvc.perform(auth(get("/api/games/borrador"), devToken)).andExpect(status().isNotFound());
    }

    @Test
    void delistedGameNotInCatalogSearch() throws Exception {
        int id = publishGame("Hades");
        buy(ownerToken, id);
        delist(id);

        mvc.perform(auth(get("/api/games").param("size", "100"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].slug", not(hasItem("hades"))));
    }

    @Test
    void ownerCanReviewDelistedGame() throws Exception {
        int id = publishGame("Hades");
        buy(ownerToken, id);
        delist(id);

        review(ownerToken, id).andExpect(status().isCreated());
        mvc.perform(auth(get("/api/games/hades/reviews"), ownerToken))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void deletedStudioShowsAsDeletedInDetailAndLibrary() throws Exception {
        int id = publishGame("Hades");
        buy(ownerToken, id);
        mvc.perform(auth(get("/api/library"), ownerToken))
                .andExpect(jsonPath("$.content[0].developerName").value("estudio"))
                .andExpect(jsonPath("$.content[0].developerDeleted").value(false));

        deleteStudio();
        mvc.perform(auth(get("/api/games/hades"), ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELISTED"))
                .andExpect(jsonPath("$.developer.displayName").doesNotExist())
                .andExpect(jsonPath("$.developer.deleted").value(true));
        mvc.perform(auth(get("/api/library"), ownerToken))
                .andExpect(jsonPath("$.content[0].developerName").doesNotExist())
                .andExpect(jsonPath("$.content[0].developerDeleted").value(true));
    }

    @Test
    void adminCannotRepublishGameOfDeletedStudio() throws Exception {
        int id = publishGame("Hades");
        deleteStudio();

        mvc.perform(auth(patch("/api/admin/games/" + id + "/status"), adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("game.developerDeleted"));
    }

    // ------------------------------------------------------------------------

    /** Baja del estudio y anonimización real: sus juegos publicados pasan a DELISTED. */
    private void deleteStudio() throws Exception {
        mvc.perform(auth(post("/api/users/me/deletion"), devToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"password123\"}")).andExpect(status().isOk());
        clock.now = T0.plus(Duration.ofDays(15));
        anonymizer.anonymize(jdbc.queryForObject("SELECT id FROM users WHERE username = 'estudio'", Long.class));
    }

    private void delist(int gameId) throws Exception {
        mvc.perform(auth(patch("/api/admin/games/" + gameId + "/status"), adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DELISTED\"}"))
                .andExpect(status().isOk());
    }

    private ResultActions review(String token, int gameId) throws Exception {
        return mvc.perform(auth(post("/api/reviews"), token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"gameId\":%d,\"recommended\":true,\"body\":\"genial\",\"language\":\"es\"}".formatted(gameId)));
    }

    private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private String register(String username, String email, String accountType) throws Exception {
        String response = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username":"%s","email":"%s","password":"password123","accountType":"%s"}"""
                        .formatted(username, email, accountType)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.accessToken");
    }

    private String login(String login, String password) throws Exception {
        String response = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"%s\",\"password\":\"%s\"}".formatted(login, password)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.accessToken");
    }

    private int createDraft(String title) throws Exception {
        List<Integer> genre = JsonPath.read(mvc.perform(get("/api/genres")).andReturn().getResponse()
                .getContentAsString(), "$[?(@.slug=='indie')].id");
        String created = mvc.perform(auth(post("/api/developer/games"), devToken).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","price":%s,"discountPercent":0,"genreIds":[%d]}"""
                                .formatted(title, "10.00", genre.getFirst())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(created, "$.id");
    }

    private int publishGame(String title) throws Exception {
        int id = createDraft(title);
        mvc.perform(auth(post("/api/developer/games/" + id + "/publish"), devToken)).andExpect(status().isOk());
        return id;
    }

    private void buy(String playerToken, int gameId) throws Exception {
        mvc.perform(auth(put("/api/cart/items/" + gameId), playerToken)).andExpect(status().isOk());
        ResultActions order = mvc.perform(auth(post("/api/orders"), playerToken)).andExpect(status().isCreated());
        int orderId = JsonPath.read(order.andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(auth(post("/api/orders/" + orderId + "/pay"), playerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(GOOD_CARD))
                .andExpect(status().isOk());
    }
}
