package com.capstone.steam.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.steam.common.exception.AccountPendingDeletionException;
import com.capstone.steam.notification.event.AccountDeletionCancelledEvent;
import com.capstone.steam.support.MutableClock;
import com.capstone.steam.user.AccountDeletionService;
import com.capstone.steam.user.User;
import com.capstone.steam.user.UserRepository;
import com.jayway.jsonpath.JsonPath;

/** Login de una cuenta en pausa y «Recuperar mi cuenta» por HTTP. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import(MutableClock.Config.class)
@RecordApplicationEvents
class AccountRecoveryIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private MutableClock clock;
    @Autowired private UserRepository userRepository;
    @Autowired private ApplicationEvents events;
    @Autowired private AccountDeletionService accountDeletionService;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void loginWhilePausedBySelfIs403WithDate() throws Exception {
        clock.now = Instant.parse("2026-11-01T10:00:00Z");
        pauseBySelf("pausa", "pausa@test.com");

        mvc.perform(login("pausa", "password123"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth.accountPendingDeletion"))
                .andExpect(jsonPath("$.deletionScheduledAt").value("2026-11-15T10:00:00Z"))
                .andExpect(jsonPath("$.detail").value("Tu cuenta se eliminará el 15/11/26. Puedes recuperarla ahora."));
        mvc.perform(login("pausa", "password123").header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
                .andExpect(jsonPath("$.detail").value("Your account will be deleted on 11/15/26. You can recover it now."));
    }

    @Test
    void loginWhilePausedByAdminIs403AdminCode() throws Exception {
        pauseByAdmin("pausaadm", "pausaadm@test.com");

        mvc.perform(login("pausaadm", "password123"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth.accountDeletionByAdmin"))
                .andExpect(jsonPath("$.deletionScheduledAt").exists());
    }

    @Test
    void wrongPasswordWhilePausedIs401() throws Exception {
        pauseBySelf("pausa2", "pausa2@test.com");

        mvc.perform(login("pausa2", "wrong-password"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Usuario o contraseña incorrectos"))
                .andExpect(jsonPath("$.deletionScheduledAt").doesNotExist());
    }

    @Test
    void recoverBySelfCancelsAndLogsIn() throws Exception {
        pauseBySelf("vuelve", "vuelve@test.com");

        mvc.perform(recover("vuelve@test.com", "password123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.username").value("vuelve"));

        mvc.perform(login("vuelve", "password123")).andExpect(status().isOk());
        assertThat(events.stream(AccountDeletionCancelledEvent.class).count()).isOne();
        assertThat(userRepository.findByUsernameIgnoreCase("vuelve").orElseThrow().isPendingDeletion()).isFalse();
    }

    @Test
    void recoverWhenAdminRequestedIs403() throws Exception {
        pauseByAdmin("noadm", "noadm@test.com");

        mvc.perform(recover("noadm", "password123"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth.accountDeletionByAdmin"));
        assertThat(userRepository.findByUsernameIgnoreCase("noadm").orElseThrow().isPendingDeletion()).isTrue();
        assertThat(events.stream(AccountDeletionCancelledEvent.class).count()).isZero();
    }

    @Test
    void recoverWithWrongPasswordIs401() throws Exception {
        pauseBySelf("clave", "clave@test.com");

        mvc.perform(recover("clave", "wrong-password")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Usuario o contraseña incorrectos"))
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.deletionScheduledAt").doesNotExist());
        assertThat(userRepository.findByUsernameIgnoreCase("clave").orElseThrow().isPendingDeletion()).isTrue();
    }

    @Test
    void recoverWithoutPendingDeletionBehavesLikeLogin() throws Exception {
        register("normal", "normal@test.com");

        mvc.perform(recover("normal", "password123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
        assertThat(events.stream(AccountDeletionCancelledEvent.class).count()).isZero();
    }

    @Test
    void oldLoginAfterAnonymizationIs401() throws Exception {
        register("anon", "anon@test.com");
        User user = userRepository.findByUsernameIgnoreCase("anon").orElseThrow();
        user.anonymize("{noop}inutilizable", clock.now);
        userRepository.saveAndFlush(user);

        mvc.perform(login("anon", "password123")).andExpect(status().isUnauthorized());
        mvc.perform(login("anon@test.com", "password123")).andExpect(status().isUnauthorized());
        mvc.perform(recover("anon", "password123")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Usuario o contraseña incorrectos"))
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test
    void recoverUnknownLoginGivesTheSameBodyAsLogin() throws Exception {
        String viaLogin = mvc.perform(login("nadie", "password123")).andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String viaRecover = mvc.perform(recover("nadie", "password123")).andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<String>read(viaRecover, "$.detail")).isEqualTo("Usuario o contraseña incorrectos");
        assertThat(JsonPath.<Object>read(viaRecover, "$.title")).isEqualTo(JsonPath.<Object>read(viaLogin, "$.title"));
        assertThat(JsonPath.<Object>read(viaRecover, "$.detail")).isEqualTo(JsonPath.<Object>read(viaLogin, "$.detail"));
        assertThat(JsonPath.<Object>read(viaRecover, "$.status")).isEqualTo(401);
    }

    @Test
    void cancelBySelfDecidesOnTheLockedRowNotOnTheStaleOne() throws Exception {
        pauseBySelf("carrera", "carrera@test.com");
        User stale = userRepository.findByUsernameIgnoreCase("carrera").orElseThrow(); // SELF, como lo leyó authenticate
        // otro admin cancela y vuelve a programar la baja como ADMIN entre la lectura y el bloqueo
        jdbc.update("update users set deletion_requested_by = 'ADMIN' where id = ?", stale.getId());

        assertThatThrownBy(() -> accountDeletionService.cancelBySelf(stale))
                .isInstanceOf(AccountPendingDeletionException.class)
                .extracting("code").isEqualTo("auth.accountDeletionByAdmin");
        assertThat(userRepository.findByUsernameIgnoreCase("carrera").orElseThrow().isPendingDeletion()).isTrue();
        assertThat(events.stream(AccountDeletionCancelledEvent.class).count()).isZero();
    }

    @Test
    void cancelBySelfOnAnAnonymizedRowFailsAsBadCredentials() throws Exception {
        pauseBySelf("purgada", "purgada@test.com");
        User stale = userRepository.findByUsernameIgnoreCase("purgada").orElseThrow(); // aún pendiente en memoria
        jdbc.update("update users set deleted_at = ?, deletion_scheduled_at = null, deletion_requested_by = null,"
                + " enabled = false where id = ?", java.sql.Timestamp.from(clock.now), stale.getId());

        assertThatThrownBy(() -> accountDeletionService.cancelBySelf(stale))
                .isInstanceOf(BadCredentialsException.class);
        assertThat(events.stream(AccountDeletionCancelledEvent.class).count()).isZero();
    }

    private void pauseBySelf(String username, String email) throws Exception {
        String token = register(username, email);
        mvc.perform(post("/api/users/me/deletion").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"password123\"}"))
                .andExpect(status().isOk());
    }

    private void pauseByAdmin(String username, String email) throws Exception {
        register(username, email);
        Long id = userRepository.findByUsernameIgnoreCase(username).orElseThrow().getId();
        String body = mvc.perform(login("admin", "Admin12345!")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String admin = JsonPath.read(body, "$.accessToken");
        mvc.perform(delete("/api/admin/users/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isOk());
    }

    private MockHttpServletRequestBuilder login(String login, String password) {
        return post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"%s\",\"password\":\"%s\"}".formatted(login, password));
    }

    private MockHttpServletRequestBuilder recover(String login, String password) {
        return post("/api/auth/recover-account").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"%s\",\"password\":\"%s\"}".formatted(login, password));
    }

    private String register(String username, String email) throws Exception {
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username":"%s","email":"%s","password":"password123","accountType":"PLAYER"}"""
                        .formatted(username, email)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }
}
