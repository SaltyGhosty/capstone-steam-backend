package com.capstone.steam.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

/** Refresh token por HTTP: rotación, detección de reutilización, logout y revocación desde el admin. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RefreshTokenIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void loginReturnsRefreshTokenAndRefreshRotatesIt() throws Exception {
        register("alyx", "alyx@test.com");
        String loginBody = loginBody("alyx");
        String refresh = JsonPath.read(loginBody, "$.refreshToken");
        assertThat(refresh).isNotBlank();
        assertThat((Integer) JsonPath.read(loginBody, "$.expiresIn")).isEqualTo(900);

        String body = refresh(refresh).andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value("alyx"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn().getResponse().getContentAsString();
        String newAccess = JsonPath.read(body, "$.accessToken");
        String newRefresh = JsonPath.read(body, "$.refreshToken");
        assertThat(newAccess).isNotBlank();
        assertThat(newRefresh).isNotBlank().isNotEqualTo(refresh);

        mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + newAccess))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alyx"));

        // el token en claro no se guarda: la BD solo tiene hashes
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens WHERE token_hash IN (?, ?)",
                Integer.class, refresh, newRefresh)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens", Integer.class)).isGreaterThanOrEqualTo(3);
    }

    @Test
    void reusingARotatedTokenRevokesTheWholeFamily() throws Exception {
        String registered = register("barney", "barney@test.com");
        String first = JsonPath.read(registered, "$.refreshToken");
        String second = JsonPath.read(refresh(first).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.refreshToken");

        refresh(first).andExpect(status().isUnauthorized());
        // la reutilización cierra la familia: el token legítimo más nuevo tampoco sirve
        refresh(second).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesTheSessionAndIsIdempotent() throws Exception {
        register("eli", "eli@test.com");
        String refresh = JsonPath.read(loginBody("eli"), "$.refreshToken");

        logout(refresh).andExpect(status().isNoContent());
        refresh(refresh).andExpect(status().isUnauthorized());
        logout(refresh).andExpect(status().isNoContent());
        logout("token-que-no-existe").andExpect(status().isNoContent());
    }

    @Test
    void disablingTheAccountKillsItsRefreshTokens() throws Exception {
        String refresh = JsonPath.read(register("mallory", "mallory@test.com"), "$.refreshToken");
        String adminToken = adminToken();

        mvc.perform(patch("/api/admin/users/" + userId("mallory") + "/enabled")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk());

        refresh(refresh).andExpect(status().isUnauthorized());
    }

    @Test
    void changingRolesKillsItsRefreshTokens() throws Exception {
        String refresh = JsonPath.read(register("judith", "judith@test.com"), "$.refreshToken");
        String adminToken = adminToken();

        mvc.perform(put("/api/admin/users/" + userId("judith") + "/roles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"roles\":[\"ROLE_DEVELOPER\"]}"))
                .andExpect(status().isOk());

        refresh(refresh).andExpect(status().isUnauthorized());
    }

    @Test
    void registerAlsoReturnsARefreshToken() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"username":"gordon","email":"gordon@test.com","password":"password123"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    @Test
    void garbageRefreshTokenReturns401Problem() throws Exception {
        refresh("basura").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("La sesión ha caducado; inicia sesión de nuevo"));
    }

    @Test
    void blankRefreshTokenReturns400() throws Exception {
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{\"refreshToken\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.refreshToken").exists());
    }

    @Test
    void wrongPasswordStillReturnsBadCredentials() throws Exception {
        register("kleiner", "kleiner@test.com");
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"kleiner\",\"password\":\"mala-contraseña\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Usuario o contraseña incorrectos"));
    }

    // ---------------------------- Utilidades ----------------------------

    private String register(String username, String email) throws Exception {
        return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username":"%s","email":"%s","password":"password123"}""".formatted(username, email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private String loginBody(String login) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"%s\",\"password\":\"password123\"}".formatted(login)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"%s\"}".formatted(refreshToken)));
    }

    private ResultActions logout(String refreshToken) throws Exception {
        return mvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"%s\"}".formatted(refreshToken)));
    }

    private String adminToken() throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"admin\",\"password\":\"Admin12345!\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private long userId(String username) {
        return jdbc.queryForObject("SELECT id FROM users WHERE username = ?", Long.class, username);
    }
}
