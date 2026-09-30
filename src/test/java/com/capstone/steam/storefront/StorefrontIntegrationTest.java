package com.capstone.steam.storefront;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

/** Carrito, wishlist, biblioteca y reseñas por HTTP. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StorefrontIntegrationTest {

    @Autowired
    private MockMvc mvc;

    private String devToken;
    private String playerToken;
    private String adminToken;
    private long playerId;
    private int gameId;

    @BeforeEach
    void setUp() throws Exception {
        devToken = register("studio", "studio@test.com", "DEVELOPER");
        playerToken = register("player", "player@test.com", "PLAYER");
        adminToken = login("admin", "Admin12345!");
        playerId = ((Number) JsonPath.read(mvc.perform(auth(get("/api/users/me"), playerToken))
                .andReturn().getResponse().getContentAsString(), "$.id")).longValue();

        List<Integer> genreIds = JsonPath.read(mvc.perform(get("/api/genres")).andReturn().getResponse()
                .getContentAsString(), "$[?(@.slug=='indie')].id");
        String created = mvc.perform(auth(post("/api/developer/games"), devToken).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Celeste","price":20.00,"discountPercent":25,"genreIds":[%d]}"""
                                .formatted(genreIds.getFirst())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        gameId = JsonPath.read(created, "$.id");
        mvc.perform(auth(post("/api/developer/games/" + gameId + "/publish"), devToken)).andExpect(status().isOk());
    }

    @Test
    void cartAndWishlistAreIdempotentAndShowDiscountedTotal() throws Exception {
        mvc.perform(auth(put("/api/cart/items/" + gameId), playerToken)).andExpect(status().isOk());
        mvc.perform(auth(put("/api/cart/items/" + gameId), playerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemCount").value(1))
                .andExpect(jsonPath("$.total").value(15.0))
                .andExpect(jsonPath("$.items[0].available").value(true));

        mvc.perform(auth(put("/api/wishlist/" + gameId), playerToken)).andExpect(status().isNoContent());
        mvc.perform(auth(get("/api/wishlist"), playerToken))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].onSale").value(true));

        mvc.perform(auth(get("/api/library/status/" + gameId), playerToken))
                .andExpect(jsonPath("$.owned").value(false))
                .andExpect(jsonPath("$.inCart").value(true))
                .andExpect(jsonPath("$.inWishlist").value(true));

        mvc.perform(auth(delete("/api/cart/items/" + gameId), playerToken))
                .andExpect(jsonPath("$.itemCount").value(0));
    }

    @Test
    void clearCartAndRemoveFromWishlist() throws Exception {
        mvc.perform(auth(put("/api/cart/items/" + gameId), playerToken)).andExpect(status().isOk());
        mvc.perform(auth(delete("/api/cart"), playerToken)).andExpect(status().isNoContent());
        mvc.perform(auth(get("/api/cart"), playerToken)).andExpect(jsonPath("$.itemCount").value(0));

        mvc.perform(auth(put("/api/wishlist/" + gameId), playerToken)).andExpect(status().isNoContent());
        mvc.perform(auth(delete("/api/wishlist/" + gameId), playerToken)).andExpect(status().isNoContent());
        mvc.perform(auth(delete("/api/wishlist/" + gameId), playerToken)).andExpect(status().isNoContent()); // idempotente
        mvc.perform(auth(get("/api/wishlist"), playerToken)).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void developerCannotPutOwnGameInCart() throws Exception {
        mvc.perform(auth(put("/api/cart/items/" + gameId), devToken)).andExpect(status().isConflict());
    }

    @Test
    void grantedGameMovesToLibraryAndLeavesCartAndWishlist() throws Exception {
        mvc.perform(auth(put("/api/cart/items/" + gameId), playerToken));
        mvc.perform(auth(put("/api/wishlist/" + gameId), playerToken));

        grantToPlayer();

        mvc.perform(auth(get("/api/library"), playerToken))
                .andExpect(jsonPath("$.content[0].slug").value("celeste"))
                .andExpect(jsonPath("$.content[0].orderId").doesNotExist());
        mvc.perform(auth(get("/api/library/status/" + gameId), playerToken))
                .andExpect(jsonPath("$.owned").value(true))
                .andExpect(jsonPath("$.inCart").value(false))
                .andExpect(jsonPath("$.inWishlist").value(false));
        // ya lo tiene: no se puede volver a añadir
        mvc.perform(auth(put("/api/cart/items/" + gameId), playerToken)).andExpect(status().isConflict());
    }

    @Test
    void reviewLifecycleKeepsGameRatingInSync() throws Exception {
        // sin tener el juego -> 403
        mvc.perform(auth(post("/api/reviews"), playerToken).contentType(MediaType.APPLICATION_JSON)
                        .content(reviewJson(5)))
                .andExpect(status().isForbidden());

        grantToPlayer();
        String created = mvc.perform(auth(post("/api/reviews"), playerToken).contentType(MediaType.APPLICATION_JSON)
                        .content(reviewJson(5)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.author.displayName").value("player"))
                .andReturn().getResponse().getContentAsString();
        int reviewId = JsonPath.read(created, "$.id");

        mvc.perform(get("/api/games/celeste"))
                .andExpect(jsonPath("$.averageRating").value(5.0))
                .andExpect(jsonPath("$.ratingCount").value(1));

        // segunda reseña del mismo juego -> 409
        mvc.perform(auth(post("/api/reviews"), playerToken).contentType(MediaType.APPLICATION_JSON)
                        .content(reviewJson(1)))
                .andExpect(status().isConflict());

        mvc.perform(auth(put("/api/reviews/" + reviewId), playerToken).contentType(MediaType.APPLICATION_JSON)
                        .content(reviewJson(3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value(3));
        mvc.perform(get("/api/games/celeste")).andExpect(jsonPath("$.averageRating").value(3.0));

        // listado público de reseñas
        mvc.perform(get("/api/games/celeste/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].body").value("Precioso"));

        // el developer no puede borrarla; el admin sí (moderación)
        mvc.perform(auth(delete("/api/reviews/" + reviewId), devToken)).andExpect(status().isForbidden());
        mvc.perform(auth(delete("/api/reviews/" + reviewId), adminToken)).andExpect(status().isNoContent());
        mvc.perform(get("/api/games/celeste"))
                .andExpect(jsonPath("$.averageRating").value(0.0))
                .andExpect(jsonPath("$.ratingCount").value(0));
    }

    @Test
    void invalidRatingReturns400() throws Exception {
        grantToPlayer();
        mvc.perform(auth(post("/api/reviews"), playerToken).contentType(MediaType.APPLICATION_JSON)
                        .content(reviewJson(6)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.rating").exists());
    }

    // ------------------------------------------------------------------

    private void grantToPlayer() throws Exception {
        mvc.perform(auth(post("/api/admin/users/" + playerId + "/library/" + gameId), adminToken))
                .andExpect(status().isCreated());
    }

    private String reviewJson(int rating) {
        return """
                {"gameId":%d,"rating":%d,"recommended":true,"body":"Precioso"}""".formatted(gameId, rating);
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
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.accessToken");
    }
}
