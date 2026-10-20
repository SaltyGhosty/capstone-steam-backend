package com.capstone.steam.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

/** D16: desactivar una cuenta o cambiar sus roles invalida el access token vigente al instante. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import(AccessTokenRevocationIntegrationTest.ClockConfig.class)
class AccessTokenRevocationIntegrationTest {

    static final class MutableClock extends Clock {
        volatile Instant now = Instant.now();
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock();
        }
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private MutableClock clock;

    @Test
    void disablingAnAccountInvalidatesItsAccessTokenImmediately() throws Exception {
        String admin = login("admin", "Admin12345!");
        String token = register("revoked1", "revoked1@test.com");
        int userId = userId(token);
        me(token).andExpect(status().isOk());

        mvc.perform(bearer(patch("/api/admin/users/" + userId + "/enabled"), admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk());

        me(token).andExpect(status().isUnauthorized());
    }

    @Test
    void changingRolesInvalidatesOldTokenAndNewLoginWorks() throws Exception {
        String admin = login("admin", "Admin12345!");
        String oldToken = register("revoked2", "revoked2@test.com");
        int userId = userId(oldToken);

        mvc.perform(bearer(put("/api/admin/users/" + userId + "/roles"), admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"roles\":[\"ROLE_USER\",\"ROLE_DEVELOPER\"]}"))
                .andExpect(status().isOk());

        me(oldToken).andExpect(status().isUnauthorized());

        clock.now = clock.now.plusSeconds(2); // el nuevo login cae en un segundo posterior a la marca
        String newToken = login("revoked2", "password123");
        me(newToken).andExpect(status().isOk());
        me(oldToken).andExpect(status().isUnauthorized());
    }

    private ResultActions me(String token) throws Exception {
        return mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private int userId(String token) throws Exception {
        return JsonPath.read(me(token).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder bearer(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private String login(String login, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"%s\",\"password\":\"%s\"}".formatted(login, password)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private String register(String username, String email) throws Exception {
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username":"%s","email":"%s","password":"password123","accountType":"PLAYER"}"""
                        .formatted(username, email)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }
}
