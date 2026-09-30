package com.capstone.steam.admin;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

/** Panel de administración: géneros, cuentas de usuario y moderación de juegos. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminIntegrationTest {

    @Autowired
    private MockMvc mvc;

    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = login("admin", "Admin12345!").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString().transform(b -> JsonPath.read(b, "$.accessToken"));
    }

    @Test
    void genreCrud() throws Exception {
        int id = JsonPath.read(mvc.perform(auth(post("/api/admin/genres")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Metroidvania\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");

        // renombrar regenera el slug
        mvc.perform(auth(put("/api/admin/genres/" + id)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Metroidvania Clásico\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("metroidvania-clasico"));

        // nombre duplicado (sin distinguir mayúsculas) y renombrar a uno existente -> 409
        mvc.perform(auth(post("/api/admin/genres")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"rpg\"}"))
                .andExpect(status().isConflict());
        mvc.perform(auth(put("/api/admin/genres/" + id)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"RPG\"}"))
                .andExpect(status().isConflict());

        mvc.perform(auth(delete("/api/admin/genres/" + id))).andExpect(status().isNoContent());
        mvc.perform(auth(delete("/api/admin/genres/" + id))).andExpect(status().isNotFound());
    }

    @Test
    void disabledAccountCannotLogInUntilReEnabled() throws Exception {
        register("mallory", "mallory@test.com", "PLAYER");
        int userId = userId("mallory");

        mvc.perform(auth(patch("/api/admin/users/" + userId + "/enabled")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
        login("mallory", "password123")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("La cuenta está desactivada"));

        mvc.perform(auth(patch("/api/admin/users/" + userId + "/enabled")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isOk());
        login("mallory", "password123").andExpect(status().isOk());
    }

    @Test
    void adminCannotDisableOwnAccount() throws Exception {
        mvc.perform(auth(patch("/api/admin/users/" + userId("admin") + "/enabled"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownUserOrGameReturns404() throws Exception {
        mvc.perform(auth(put("/api/admin/users/999999/roles")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"ROLE_USER\"]}"))
                .andExpect(status().isNotFound());
        mvc.perform(auth(patch("/api/admin/games/999999/status")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DELISTED\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void moderationRules() throws Exception {
        String dev = register("devco", "devco@test.com", "DEVELOPER");
        int gameId = JsonPath.read(mvc.perform(post("/api/developer/games")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + dev).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Moderated\",\"price\":5.00}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");

        // pasar a borrador es cosa del developer; "republicar" solo aplica a DELISTED
        mvc.perform(auth(patch("/api/admin/games/" + gameId + "/status")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DRAFT\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(auth(patch("/api/admin/games/" + gameId + "/status")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isConflict());

        mvc.perform(auth(patch("/api/admin/games/" + gameId + "/status")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DELISTED\"}"))
                .andExpect(status().isOk());
        mvc.perform(auth(get("/api/admin/games")).param("status", "DELISTED").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].status", everyItem(is("DELISTED"))));
        mvc.perform(auth(patch("/api/admin/games/" + gameId + "/status")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    // ------------------------------------------------------------------

    private int userId(String username) throws Exception {
        String users = mvc.perform(auth(get("/api/admin/users")).param("size", "100"))
                .andReturn().getResponse().getContentAsString();
        java.util.List<Integer> ids = JsonPath.read(users, "$.content[?(@.username=='" + username + "')].id");
        return ids.getFirst();
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken);
    }

    private org.springframework.test.web.servlet.ResultActions login(String login, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"%s\",\"password\":\"%s\"}".formatted(login, password)));
    }

    private String register(String username, String email, String accountType) throws Exception {
        String response = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username":"%s","email":"%s","password":"password123","accountType":"%s"}"""
                        .formatted(username, email, accountType)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.accessToken");
    }
}
