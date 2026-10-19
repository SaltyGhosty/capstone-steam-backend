package com.capstone.steam.friend;

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

/** Amigos, solicitudes, búsqueda y perfil por HTTP. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FriendIntegrationTest {

    @Autowired
    private MockMvc mvc;

    private String ana;
    private String leo;
    private String sam;
    private String studio;
    private String adminToken;
    private long leoId;

    @BeforeEach
    void setUp() throws Exception {
        ana = register("ana", "PLAYER");
        leo = register("leo", "PLAYER");
        sam = register("sam", "PLAYER");
        studio = register("studio", "DEVELOPER");
        adminToken = login("admin", "Admin12345!");
        leoId = ((Number) JsonPath.read(mvc.perform(auth(get("/api/users/me"), leo))
                .andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    // ------------------------------ Amigos ------------------------------

    @Test
    void searchFindsPlayersOnlyAndExcludesSelfAndDevelopers() throws Exception {
        mvc.perform(auth(get("/api/users/search").param("q", "le"), ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].user.username").value("leo"))
                .andExpect(jsonPath("$[0].relation").value("NONE"))
                .andExpect(jsonPath("$..email").doesNotExist());
        // ni ella misma ni el estudio salen
        mvc.perform(auth(get("/api/users/search").param("q", "an"), ana)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(auth(get("/api/users/search").param("q", "studio"), ana)).andExpect(jsonPath("$.length()").value(0));
        // sin distinguir mayúsculas
        mvc.perform(auth(get("/api/users/search").param("q", "LEO"), ana)).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void searchTreatsPercentAndUnderscoreAsLiterals() throws Exception {
        mvc.perform(auth(get("/api/users/search").param("q", "%%"), ana)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(auth(get("/api/users/search").param("q", "__"), ana)).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void searchRequiresAtLeastTwoCharacters() throws Exception {
        mvc.perform(auth(get("/api/users/search").param("q", "a"), ana)).andExpect(status().isBadRequest());
        mvc.perform(auth(get("/api/users/search").param("q", " a "), ana)).andExpect(status().isBadRequest());
        mvc.perform(auth(get("/api/users/search"), ana)).andExpect(status().isBadRequest());
    }

    @Test
    void pendingRequestsFromDisabledAccountsAreHiddenAndNotAcceptable() throws Exception {
        int id = idOf(sendRequest(leo, "ana"));
        mvc.perform(auth(patch("/api/admin/users/" + leoId + "/enabled"), adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk());
        mvc.perform(auth(get("/api/friends/requests"), ana)).andExpect(jsonPath("$.incoming.length()").value(0));
        mvc.perform(auth(post("/api/friends/requests/" + id + "/accept"), ana)).andExpect(status().isNotFound());
        mvc.perform(auth(post("/api/friends/requests/" + id + "/reject"), ana)).andExpect(status().isNotFound());
    }

    @Test
    void pendingRequestsFromAccountsThatBecameDevelopersAreHiddenAndNotAcceptable() throws Exception {
        int id = idOf(sendRequest(leo, "ana"));
        mvc.perform(auth(put("/api/admin/users/" + leoId + "/roles"), adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"roles\":[\"ROLE_DEVELOPER\"]}"))
                .andExpect(status().isOk());
        mvc.perform(auth(get("/api/friends/requests"), ana)).andExpect(jsonPath("$.incoming.length()").value(0));
        mvc.perform(auth(post("/api/friends/requests/" + id + "/accept"), ana)).andExpect(status().isNotFound());
    }

    @Test
    void searchQueryLongerThan50CharactersIsRejected() throws Exception {
        mvc.perform(auth(get("/api/users/search").param("q", "a".repeat(51)), ana))
                .andExpect(status().isBadRequest());
        mvc.perform(auth(get("/api/users/search").param("q", "a".repeat(50)), ana))
                .andExpect(status().isOk());
    }

    @Test
    void searchRequiresSession() throws Exception {
        mvc.perform(get("/api/users/search").param("q", "le")).andExpect(status().isUnauthorized());
    }

    @Test
    void disabledAccountsDisappearFromSearch() throws Exception {
        mvc.perform(auth(patch("/api/admin/users/" + leoId + "/enabled"), adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk());
        mvc.perform(auth(get("/api/users/search").param("q", "leo"), ana)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(auth(post("/api/friends/requests"), ana).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"leo\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void requestThenAcceptMakesThemFriends() throws Exception {
        mvc.perform(sendRequest(ana, "leo"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.user.username").value("leo"));
        // repetir -> 409
        mvc.perform(sendRequest(ana, "leo")).andExpect(status().isConflict());

        mvc.perform(auth(get("/api/friends/requests"), leo))
                .andExpect(jsonPath("$.incoming.length()").value(1))
                .andExpect(jsonPath("$.incoming[0].user.username").value("ana"))
                .andExpect(jsonPath("$.outgoing.length()").value(0));
        String requests = mvc.perform(auth(get("/api/friends/requests"), ana))
                .andExpect(jsonPath("$.outgoing.length()").value(1))
                .andExpect(jsonPath("$.outgoing[0].user.username").value("leo"))
                .andReturn().getResponse().getContentAsString();
        int requestId = JsonPath.read(requests, "$.outgoing[0].id");

        // la búsqueda refleja el estado de la solicitud
        mvc.perform(auth(get("/api/users/search").param("q", "leo"), ana))
                .andExpect(jsonPath("$[0].relation").value("REQUEST_SENT"))
                .andExpect(jsonPath("$[0].requestId").value(requestId));
        mvc.perform(auth(get("/api/users/search").param("q", "ana"), leo))
                .andExpect(jsonPath("$[0].relation").value("REQUEST_RECEIVED"))
                .andExpect(jsonPath("$[0].requestId").value(requestId));

        // quien pidió no puede aceptar su propia solicitud
        mvc.perform(auth(post("/api/friends/requests/" + requestId + "/accept"), ana)).andExpect(status().isNotFound());

        mvc.perform(auth(post("/api/friends/requests/" + requestId + "/accept"), leo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        mvc.perform(auth(get("/api/friends"), ana))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].user.username").value("leo"))
                .andExpect(jsonPath("$[0].since").exists());
        mvc.perform(auth(get("/api/friends"), leo))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].user.username").value("ana"));
        mvc.perform(auth(get("/api/users/search").param("q", "leo"), ana))
                .andExpect(jsonPath("$[0].relation").value("FRIENDS"));
        mvc.perform(auth(get("/api/friends/requests"), leo)).andExpect(jsonPath("$.incoming.length()").value(0));

        // ya son amigos -> 409 en cualquier dirección
        mvc.perform(sendRequest(ana, "leo")).andExpect(status().isConflict());
        mvc.perform(sendRequest(leo, "ana")).andExpect(status().isConflict());
    }

    @Test
    void crossedRequestsAreAcceptedAutomatically() throws Exception {
        mvc.perform(sendRequest(sam, "ana")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));
        mvc.perform(sendRequest(ana, "sam"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
        mvc.perform(auth(get("/api/friends"), sam)).andExpect(jsonPath("$[0].user.username").value("ana"));
    }

    @Test
    void invalidRequestTargets() throws Exception {
        mvc.perform(sendRequest(ana, "ana")).andExpect(status().isBadRequest());
        mvc.perform(sendRequest(ana, "studio")).andExpect(status().isNotFound());
        mvc.perform(sendRequest(ana, "admin")).andExpect(status().isNotFound());
        mvc.perform(sendRequest(ana, "nadie")).andExpect(status().isNotFound());
        mvc.perform(sendRequest(studio, "ana")).andExpect(status().isForbidden());
        mvc.perform(sendRequest(adminToken, "ana")).andExpect(status().isForbidden());
        mvc.perform(auth(post("/api/friends/requests"), ana).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectCancelAndRemoveDeleteTheRow() throws Exception {
        // rechazar
        int first = idOf(sendRequest(ana, "leo"));
        mvc.perform(auth(post("/api/friends/requests/" + first + "/reject"), ana)).andExpect(status().isNotFound());
        mvc.perform(auth(post("/api/friends/requests/" + first + "/reject"), leo)).andExpect(status().isNoContent());
        mvc.perform(auth(get("/api/friends/requests"), leo)).andExpect(jsonPath("$.incoming.length()").value(0));

        // cancelar (solo quien la envió)
        int second = idOf(sendRequest(ana, "leo"));
        mvc.perform(auth(delete("/api/friends/requests/" + second), leo)).andExpect(status().isNotFound());
        mvc.perform(auth(delete("/api/friends/requests/" + second), ana)).andExpect(status().isNoContent());
        mvc.perform(auth(get("/api/friends/requests"), ana)).andExpect(jsonPath("$.outgoing.length()").value(0));

        // borrar una amistad
        int third = idOf(sendRequest(ana, "leo"));
        mvc.perform(auth(post("/api/friends/requests/" + third + "/accept"), leo)).andExpect(status().isOk());
        // una amistad aceptada ya no se cancela ni se rechaza como solicitud
        mvc.perform(auth(delete("/api/friends/requests/" + third), ana)).andExpect(status().isNotFound());
        mvc.perform(auth(delete("/api/friends/leo"), ana)).andExpect(status().isNoContent());
        mvc.perform(auth(delete("/api/friends/leo"), ana)).andExpect(status().isNotFound());
        mvc.perform(auth(get("/api/friends"), leo)).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void acceptingSomeoneElsesRequestIsNotFound() throws Exception {
        int id = idOf(sendRequest(ana, "leo"));
        mvc.perform(auth(post("/api/friends/requests/" + id + "/accept"), sam)).andExpect(status().isNotFound());
        mvc.perform(auth(post("/api/friends/requests/99999/accept"), leo)).andExpect(status().isNotFound());
    }

    @Test
    void friendsAreSortedByDisplayName() throws Exception {
        mvc.perform(auth(patch("/api/users/me"), leo).contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"Zeta\"}")).andExpect(status().isOk());
        mvc.perform(auth(patch("/api/users/me"), sam).contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"Alfa\"}")).andExpect(status().isOk());
        int a = idOf(sendRequest(ana, "leo"));
        int b = idOf(sendRequest(ana, "sam"));
        mvc.perform(auth(post("/api/friends/requests/" + a + "/accept"), leo)).andExpect(status().isOk());
        mvc.perform(auth(post("/api/friends/requests/" + b + "/accept"), sam)).andExpect(status().isOk());

        mvc.perform(auth(get("/api/friends"), ana))
                .andExpect(jsonPath("$[0].user.displayName").value("Alfa"))
                .andExpect(jsonPath("$[1].user.displayName").value("Zeta"));
    }

    @Test
    void friendEndpointsRequireSession() throws Exception {
        mvc.perform(get("/api/friends")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/friends/requests")).andExpect(status().isUnauthorized());
    }

    // ------------------------------ Perfil ------------------------------

    @Test
    void privateProfileHidesLibraryAndFriendsFromStrangers() throws Exception {
        mvc.perform(auth(get("/api/users/leo/profile"), ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value("leo"))
                .andExpect(jsonPath("$.profilePublic").value(false))
                .andExpect(jsonPath("$.relation").value("NONE"))
                .andExpect(jsonPath("$.visible").value(false))
                .andExpect(jsonPath("$.memberSince").exists())
                .andExpect(jsonPath("$.library").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.friends").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void publicProfileIsVisibleToAnyLoggedUser() throws Exception {
        mvc.perform(auth(patch("/api/users/me"), leo).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profilePublic\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profilePublic").value(true));
        // otro PATCH sin el campo no lo cambia
        mvc.perform(auth(patch("/api/users/me"), leo).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Leo L\"}"))
                .andExpect(jsonPath("$.profilePublic").value(true));

        mvc.perform(auth(get("/api/users/leo/profile"), ana))
                .andExpect(jsonPath("$.visible").value(true))
                .andExpect(jsonPath("$.profilePublic").value(true))
                .andExpect(jsonPath("$.relation").value("NONE"))
                .andExpect(jsonPath("$.library.length()").value(0))
                .andExpect(jsonPath("$.friends.length()").value(0));
    }

    @Test
    void friendsSeeAPrivateProfileAndItShowsFriends() throws Exception {
        befriend(ana, "leo", leo);
        befriend(sam, "leo", leo);

        mvc.perform(auth(get("/api/users/leo/profile"), ana))
                .andExpect(jsonPath("$.relation").value("FRIENDS"))
                .andExpect(jsonPath("$.visible").value(true))
                .andExpect(jsonPath("$.friends.length()").value(2))
                .andExpect(jsonPath("$.friends[0].user.username").exists());
        mvc.perform(auth(get("/api/users/ana/profile"), leo))
                .andExpect(jsonPath("$.relation").value("FRIENDS"))
                .andExpect(jsonPath("$.visible").value(true));
    }

    @Test
    void pendingRequestDoesNotUnlockThePrivateProfile() throws Exception {
        mvc.perform(sendRequest(ana, "leo")).andExpect(status().isCreated());
        mvc.perform(auth(get("/api/users/leo/profile"), ana))
                .andExpect(jsonPath("$.relation").value("REQUEST_SENT"))
                .andExpect(jsonPath("$.requestId").exists())
                .andExpect(jsonPath("$.visible").value(false))
                .andExpect(jsonPath("$.library").value(org.hamcrest.Matchers.nullValue()));
        mvc.perform(auth(get("/api/users/ana/profile"), leo))
                .andExpect(jsonPath("$.relation").value("REQUEST_RECEIVED"))
                .andExpect(jsonPath("$.visible").value(false));
    }

    @Test
    void ownProfileIsAlwaysVisible() throws Exception {
        mvc.perform(auth(get("/api/users/ana/profile"), ana))
                .andExpect(jsonPath("$.relation").value("SELF"))
                .andExpect(jsonPath("$.visible").value(true))
                .andExpect(jsonPath("$.library").isArray());
    }

    @Test
    void profileNeverExposesEmail() throws Exception {
        mvc.perform(auth(patch("/api/users/me"), leo).contentType(MediaType.APPLICATION_JSON)
                .content("{\"profilePublic\":true}")).andExpect(status().isOk());
        befriend(ana, "leo", leo);
        mvc.perform(auth(get("/api/users/leo/profile"), ana)).andExpect(jsonPath("$..email").doesNotExist());
        mvc.perform(auth(get("/api/users/ana/profile"), ana)).andExpect(jsonPath("$..email").doesNotExist());
    }

    @Test
    void developersAdminsDisabledAndUnknownUsersHaveNoProfile() throws Exception {
        mvc.perform(auth(get("/api/users/studio/profile"), ana)).andExpect(status().isNotFound());
        mvc.perform(auth(get("/api/users/admin/profile"), ana)).andExpect(status().isNotFound());
        mvc.perform(auth(get("/api/users/nadie/profile"), ana)).andExpect(status().isNotFound());

        mvc.perform(auth(patch("/api/admin/users/" + leoId + "/enabled"), adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk());
        mvc.perform(auth(get("/api/users/leo/profile"), ana)).andExpect(status().isNotFound());
    }

    @Test
    void libraryShowsPlaytimeMostRecentFirst() throws Exception {
        long gameA = publishGame("Celeste");
        long gameB = publishGame("Hades");
        grant(leoId, gameA);
        grant(leoId, gameB);
        mvc.perform(auth(post("/api/library/" + gameA + "/play"), leo).contentType(MediaType.APPLICATION_JSON)
                .content("{\"minutes\":90}")).andExpect(status().isOk());
        mvc.perform(auth(patch("/api/users/me"), leo).contentType(MediaType.APPLICATION_JSON)
                .content("{\"profilePublic\":true}")).andExpect(status().isOk());

        String body = mvc.perform(auth(get("/api/users/leo/profile"), ana))
                .andExpect(jsonPath("$.visible").value(true))
                .andExpect(jsonPath("$.library.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        List<Integer> minutes = JsonPath.read(body, "$.library[?(@.slug=='celeste')].playtimeMinutes");
        org.assertj.core.api.Assertions.assertThat(minutes).containsExactly(90);
        List<String> titles = JsonPath.read(body, "$.library[*].title");
        org.assertj.core.api.Assertions.assertThat(titles).containsExactlyInAnyOrder("Celeste", "Hades");
        mvc.perform(auth(get("/api/users/leo/profile"), ana))
                .andExpect(jsonPath("$.library[0].gameId").exists())
                .andExpect(jsonPath("$.library[0].slug").exists())
                .andExpect(jsonPath("$..email").doesNotExist());
    }

    @Test
    void profileEndpointRequiresSession() throws Exception {
        mvc.perform(get("/api/users/leo/profile")).andExpect(status().isUnauthorized());
    }


    // ------------------------------ Utilidades ------------------------------

    private long publishGame(String title) throws Exception {
        List<Integer> genreIds = JsonPath.read(mvc.perform(get("/api/genres")).andReturn().getResponse()
                .getContentAsString(), "$[?(@.slug=='indie')].id");
        String created = mvc.perform(auth(post("/api/developer/games"), studio).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","price":20.00,"discountPercent":0,"genreIds":[%d]}"""
                                .formatted(title, genreIds.getFirst())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(created, "$.id");
        mvc.perform(auth(post("/api/developer/games/" + id + "/publish"), studio)).andExpect(status().isOk());
        return id;
    }

    private void grant(long userId, long gameId) throws Exception {
        mvc.perform(auth(post("/api/admin/users/" + userId + "/library/" + gameId), adminToken))
                .andExpect(status().isCreated());
    }

    private void befriend(String requesterToken, String addresseeUsername, String addresseeToken) throws Exception {
        int id = idOf(sendRequest(requesterToken, addresseeUsername));
        mvc.perform(auth(post("/api/friends/requests/" + id + "/accept"), addresseeToken)).andExpect(status().isOk());
    }

    private MockHttpServletRequestBuilder sendRequest(String token, String username) {
        return auth(post("/api/friends/requests"), token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\"}".formatted(username));
    }

    private int idOf(MockHttpServletRequestBuilder request) throws Exception {
        String body = mvc.perform(request).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private String register(String username, String accountType) throws Exception {
        String response = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username":"%s","email":"%s@test.com","password":"password123","accountType":"%s"}"""
                        .formatted(username, username, accountType)))
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
