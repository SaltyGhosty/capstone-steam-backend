package com.capstone.steam.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.steam.support.MutableClock;
import com.jayway.jsonpath.JsonPath;

/** Pedir y cancelar la baja de una cuenta, la propia y la del admin, por HTTP. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import(MutableClock.Config.class)
class AccountDeletionIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private MutableClock clock;
    @Autowired private UserRepository userRepository;

    @Test
    void ownDeletionWithWrongPasswordIs400AndChangesNothing() throws Exception {
        String token = register("wrongpw", "wrongpw@test.com")[0];

        mvc.perform(bearer(post("/api/users/me/deletion"), token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"nope-nope\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("user.deletionWrongPassword"));

        mvc.perform(bearer(get("/api/users/me"), token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.deletionScheduledAt").doesNotExist());
        assertThat(userRepository.findByUsernameIgnoreCase("wrongpw").orElseThrow().isPendingDeletion()).isFalse();
    }

    @Test
    void ownDeletionSchedulesInFourteenDaysAndCutsSessions() throws Exception {
        clock.now = Instant.parse("2026-11-01T10:00:00Z");
        String[] tokens = register("leaver", "leaver@test.com");

        String body = mvc.perform(bearer(post("/api/users/me/deletion"), tokens[0])
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"password123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(Instant.parse(JsonPath.read(body, "$.deletionScheduledAt")))
                .isEqualTo(clock.now.plus(Duration.ofDays(14)));
        User user = userRepository.findByUsernameIgnoreCase("leaver").orElseThrow();
        assertThat(user.getDeletionRequestedBy()).isEqualTo(DeletionRequester.SELF);
        mvc.perform(bearer(get("/api/users/me"), tokens[0])).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"%s\"}".formatted(tokens[1])))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void lastAdminCannotDeleteOwnAccount() throws Exception {
        String admin = login("admin", "Admin12345!");

        mvc.perform(bearer(post("/api/users/me/deletion"), admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"Admin12345!\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("user.lastAdmin"));
    }

    @Test
    void adminSchedulesAndCancelsDeletion() throws Exception {
        String admin = login("admin", "Admin12345!");
        int id = userId(register("victim", "victim@test.com")[0]);

        mvc.perform(bearer(delete("/api/admin/users/" + id), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletionRequestedBy").value("ADMIN"))
                .andExpect(jsonPath("$.deletionScheduledAt").exists());
        // repetirlo no cambia nada (idempotente)
        mvc.perform(bearer(delete("/api/admin/users/" + id), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletionRequestedBy").value("ADMIN"));

        mvc.perform(bearer(post("/api/admin/users/" + id + "/deletion/cancel"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletionRequestedBy").doesNotExist())
                .andExpect(jsonPath("$.deletionScheduledAt").doesNotExist());
        mvc.perform(bearer(post("/api/admin/users/" + id + "/deletion/cancel"), admin))
                .andExpect(status().isOk());
    }

    @Test
    void adminCannotDeleteSelfFromPanel() throws Exception {
        String admin = login("admin", "Admin12345!");
        int adminId = userId(admin);

        mvc.perform(bearer(delete("/api/admin/users/" + adminId), admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("user.cannotDeleteSelf"));
    }

    @Test
    void pausedPlayerIsHiddenFromSearchAndProfile() throws Exception {
        String viewer = register("viewer", "viewer@test.com")[0];
        String[] paused = register("pausedone", "pausedone@test.com");
        mvc.perform(bearer(get("/api/users/search").param("q", "pausedone"), viewer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));

        mvc.perform(bearer(post("/api/users/me/deletion"), paused[0]).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"password123\"}"))
                .andExpect(status().isOk());

        mvc.perform(bearer(get("/api/users/search").param("q", "pausedone"), viewer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(bearer(get("/api/users/pausedone/profile"), viewer)).andExpect(status().isNotFound());
    }

    private MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private int userId(String token) throws Exception {
        return JsonPath.read(mvc.perform(bearer(get("/api/users/me"), token)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private String login(String login, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"%s\",\"password\":\"%s\"}".formatted(login, password)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    /** @return {accessToken, refreshToken} */
    private String[] register(String username, String email) throws Exception {
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username":"%s","email":"%s","password":"password123","accountType":"PLAYER"}"""
                        .formatted(username, email)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return new String[] { JsonPath.read(body, "$.accessToken"), JsonPath.read(body, "$.refreshToken") };
    }
}
