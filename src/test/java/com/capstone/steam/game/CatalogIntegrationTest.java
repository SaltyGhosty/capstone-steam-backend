package com.capstone.steam.game;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

/** Ciclo de vida de un juego por HTTP: developer crea y publica, el público lo ve, el admin lo retira. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CatalogIntegrationTest {

    @Autowired
    private MockMvc mvc;

    private String devToken;
    private String playerToken;
    private String adminToken;
    private int rpgId;

    @BeforeEach
    void setUp() throws Exception {
        devToken = register("valve", "valve@test.com", "DEVELOPER");
        playerToken = register("gamer", "gamer@test.com", "PLAYER");
        adminToken = login("admin", "Admin12345!");
        String genres = mvc.perform(get("/api/genres")).andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(genres, "$[?(@.slug=='rpg')].id");
        rpgId = ids.getFirst();
    }

    @Test
    void developerCreatesAndPublishesGameThatThenAppearsInCatalog() throws Exception {
        String created = mvc.perform(auth(post("/api/developer/games"), devToken).contentType(MediaType.APPLICATION_JSON)
                        .content(gameJson("Portal 2", "19.99", 50, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("portal-2"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.finalPrice").value(10.0))
                .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(created, "$.id");

        // en borrador no es visible al público
        mvc.perform(get("/api/games/portal-2")).andExpect(status().isNotFound());

        mvc.perform(auth(post("/api/developer/games/" + id + "/publish"), devToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        mvc.perform(get("/api/games/portal-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.developer.displayName").value("valve"));
        mvc.perform(get("/api/games").param("q", "portal").param("genres", "rpg").param("onSale", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].genres[0].slug").value("rpg"));
    }

    @Test
    void duplicateTitlesGetUniqueSlugs() throws Exception {
        createGame("Portal 2");
        mvc.perform(auth(post("/api/developer/games"), devToken).contentType(MediaType.APPLICATION_JSON)
                        .content(gameJson("Portal 2", "9.99", 0, null)))
                .andExpect(jsonPath("$.slug").value("portal-2-2"));
    }

    @Test
    void onlyTheOwningDeveloperCanManageAGame() throws Exception {
        int id = createGame("Half-Life 3");

        mvc.perform(auth(post("/api/developer/games"), playerToken).contentType(MediaType.APPLICATION_JSON)
                        .content(gameJson("Nope", "1.00", 0, null)))
                .andExpect(status().isForbidden());

        String otherDev = register("rival", "rival@test.com", "DEVELOPER");
        mvc.perform(auth(put("/api/developer/games/" + id), otherDev).contentType(MediaType.APPLICATION_JSON)
                        .content(gameJson("Hacked", "0.00", 0, null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateWithStaleVersionReturns409() throws Exception {
        int id = createGame("Portal");
        mvc.perform(auth(put("/api/developer/games/" + id), devToken).contentType(MediaType.APPLICATION_JSON)
                        .content(gameJson("Portal", "5.00", 0, 0L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
        // otra pestaña todavía tenía la versión 0
        mvc.perform(auth(put("/api/developer/games/" + id), devToken).contentType(MediaType.APPLICATION_JSON)
                        .content(gameJson("Portal", "7.00", 0, 0L)))
                .andExpect(status().isConflict());
    }

    @Test
    void adminDelistsGameAndItDisappearsFromCatalog() throws Exception {
        int id = createGame("Left 4 Dead");
        mvc.perform(auth(post("/api/developer/games/" + id + "/publish"), devToken)).andExpect(status().isOk());

        mvc.perform(auth(patch("/api/admin/games/" + id + "/status"), adminToken).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DELISTED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELISTED"));

        mvc.perform(get("/api/games/left-4-dead")).andExpect(status().isNotFound());
        mvc.perform(get("/api/games").param("size", "100"))
                .andExpect(jsonPath("$.content[*].slug", not(hasItem("left-4-dead"))));
        // el developer no puede saltarse la moderación
        mvc.perform(auth(post("/api/developer/games/" + id + "/publish"), devToken)).andExpect(status().isConflict());
    }

    @Test
    void developerPanelLifecycle() throws Exception {
        int published = createGame("Published One");
        int draft = createGame("Draft One");
        mvc.perform(auth(post("/api/developer/games/" + published + "/publish"), devToken)).andExpect(status().isOk());

        mvc.perform(auth(get("/api/developer/games"), devToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(auth(get("/api/developer/games/" + draft), devToken))
                .andExpect(jsonPath("$.status").value("DRAFT"));

        // descuento masivo: solo afecta a los publicados
        mvc.perform(auth(post("/api/developer/games/discount"), devToken).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"percent\":50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(1));
        mvc.perform(get("/api/games/published-one")).andExpect(jsonPath("$.finalPrice").value(5.0));

        // un publicado no se puede borrar; se retira a borrador y entonces sí
        mvc.perform(auth(delete("/api/developer/games/" + published), devToken)).andExpect(status().isConflict());
        mvc.perform(auth(post("/api/developer/games/" + published + "/unpublish"), devToken))
                .andExpect(jsonPath("$.status").value("DRAFT"));
        mvc.perform(auth(post("/api/developer/games/" + published + "/unpublish"), devToken))
                .andExpect(status().isConflict());
        mvc.perform(auth(delete("/api/developer/games/" + published), devToken)).andExpect(status().isNoContent());
        mvc.perform(auth(get("/api/developer/games/" + published), devToken)).andExpect(status().isNotFound());
    }

    @Test
    void unknownGenreIdIsRejected() throws Exception {
        mvc.perform(auth(post("/api/developer/games"), devToken).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"X\",\"price\":1.00,\"genreIds\":[999999]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void coverUrlMustBeHttpOrHttps() throws Exception {
        for (String unsafe : new String[] {"file:///C:/x.png", "ftp://a.test/x.png", "javascript:alert(1)",
                " https://img.test/c.jpg", "https://img.test/c.jpg "}) {
            mvc.perform(auth(post("/api/developer/games"), devToken).contentType(MediaType.APPLICATION_JSON)
                            .content(gameWithCover("Unsafe cover", unsafe)))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(auth(post("/api/developer/games"), devToken).contentType(MediaType.APPLICATION_JSON)
                        .content(gameWithCover("Safe cover", "HTTPS://img.test/c.jpg")))
                .andExpect(status().isCreated());
        mvc.perform(auth(post("/api/developer/games"), devToken).contentType(MediaType.APPLICATION_JSON)
                        .content(gameWithCover("Mixed case cover", "Http://img.test/c.jpg")))
                .andExpect(status().isCreated());
        mvc.perform(auth(post("/api/developer/games"), devToken).contentType(MediaType.APPLICATION_JSON)
                        .content(gameWithCover("No cover", "")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.coverUrl").doesNotExist());
    }

    private String gameWithCover(String title, String coverUrl) {
        return """
                {"title":"%s","price":9.99,"genreIds":[%d],"coverUrl":"%s"}""".formatted(title, rpgId, coverUrl);
    }

    @Test
    void invalidSearchParamsReturn400() throws Exception {
        mvc.perform(get("/api/games").param("sort", "cheapest")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/games").param("minPrice", "20").param("maxPrice", "10")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/games").param("size", "500")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/games").param("minPositivePercent", "101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/games").param("minPositivePercent", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/games").param("minPositivePercent", "80")).andExpect(status().isOk());
    }

    @Test
    void genresArePublicButOnlyAdminCanCreateThem() throws Exception {
        mvc.perform(auth(post("/api/admin/genres"), playerToken).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Roguelike\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(auth(post("/api/admin/genres"), adminToken).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Roguelike\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("roguelike"));
        mvc.perform(get("/api/genres")).andExpect(jsonPath("$[*].slug", hasItem("roguelike")));
    }

    // ------------------------------------------------------------------

    private int createGame(String title) throws Exception {
        String body = mvc.perform(auth(post("/api/developer/games"), devToken).contentType(MediaType.APPLICATION_JSON)
                        .content(gameJson(title, "9.99", 0, null)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private String gameJson(String title, String price, int discount, Long version) {
        return """
                {"title":"%s","price":%s,"discountPercent":%d,"releaseDate":"2011-04-19",
                 "genreIds":[%d],"version":%s}""".formatted(title, price, discount, rpgId, version);
    }

    private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private String register(String username, String email, String accountType) throws Exception {
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username":"%s","email":"%s","password":"password123","accountType":"%s"}"""
                        .formatted(username, email, accountType)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private String login(String login, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"%s\",\"password\":\"%s\"}".formatted(login, password)))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }
}
