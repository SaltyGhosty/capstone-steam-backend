package com.capstone.steam.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import jakarta.persistence.EntityManager;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.steam.common.exception.ConflictException;
import com.capstone.steam.notification.event.AccountDeletionScheduledEvent;
import com.capstone.steam.support.MutableClock;
import com.jayway.jsonpath.JsonPath;

/** Pedir y cancelar la baja de una cuenta, la propia y la del admin, por HTTP. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import(MutableClock.Config.class)
@RecordApplicationEvents
class AccountDeletionIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private MutableClock clock;
    @Autowired private UserRepository userRepository;
    @Autowired private AccountDeletionService service;
    @Autowired private UserService userService;
    @Autowired private ApplicationEvents events;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager em;

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
    void wrongPasswordOfTheLastAdminIs400NotConflict() throws Exception {
        String admin = login("admin", "Admin12345!");

        // la contraseña se comprueba antes de bloquear a los admins: un error de contraseña no revela nada más
        mvc.perform(bearer(post("/api/users/me/deletion"), admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"nope-nope\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("user.deletionWrongPassword"));
    }

    @Test
    void adminSchedulesAndCancelsDeletion() throws Exception {
        String admin = login("admin", "Admin12345!");
        String[] victim = register("victim", "victim@test.com");
        int id = userId(victim[0]);

        String first = mvc.perform(bearer(delete("/api/admin/users/" + id), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletionRequestedBy").value("ADMIN"))
                .andExpect(jsonPath("$.deletionScheduledAt").exists())
                .andReturn().getResponse().getContentAsString();
        mvc.perform(bearer(get("/api/users/me"), victim[0])).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"%s\"}".formatted(victim[1])))
                .andExpect(status().isUnauthorized());
        // repetirlo no cambia nada (idempotente): ni la fecha ni quien la pidio
        clock.now = clock.now.plus(Duration.ofDays(1));
        String second = mvc.perform(bearer(delete("/api/admin/users/" + id), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletionRequestedBy").value("ADMIN"))
                .andReturn().getResponse().getContentAsString();
        // la BD guarda microsegundos: se compara a milisegundos
        assertThat(Instant.parse(JsonPath.read(second, "$.deletionScheduledAt")).truncatedTo(java.time.temporal.ChronoUnit.MILLIS))
                .isEqualTo(Instant.parse(JsonPath.read(first, "$.deletionScheduledAt")).truncatedTo(java.time.temporal.ChronoUnit.MILLIS));

        mvc.perform(bearer(post("/api/admin/users/" + id + "/deletion/cancel"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletionRequestedBy").doesNotExist())
                .andExpect(jsonPath("$.deletionScheduledAt").doesNotExist());
        mvc.perform(bearer(post("/api/admin/users/" + id + "/deletion/cancel"), admin))
                .andExpect(status().isOk());
    }

    @Test
    void adminCanScheduleAnotherAdminButNotTheLastCountingOne() throws Exception {
        String admin = login("admin", "Admin12345!");
        int adminId = userId(admin);
        int otherId = userId(register("admin2", "admin2@test.com")[0]);
        mvc.perform(bearer(put("/api/admin/users/" + otherId + "/roles"), admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"roles\":[\"ROLE_USER\",\"ROLE_ADMIN\"]}"))
                .andExpect(status().isOk());

        // con un segundo admin que cuenta, se puede programar al otro
        mvc.perform(bearer(delete("/api/admin/users/" + otherId), admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.deletionRequestedBy").value("ADMIN"));

        // ahora "admin" es el unico que cuenta: quien no sea el mismo no puede programarlo
        assertThatThrownBy(() -> service.scheduleByAdmin((long) otherId, (long) adminId))
                .isInstanceOf(ConflictException.class)
                .extracting(e -> ((ConflictException) e).getCode()).isEqualTo("user.lastAdmin");
    }

    @Test
    void stateIsReReadUnderTheLockSoAConcurrentScheduleIsNotDuplicated() throws Exception {
        long adminId = userId(login("admin", "Admin12345!"));
        long id = userId(register("racer", "racer@test.com")[0]);
        User loaded = userRepository.findWithRolesById(id).orElseThrow(); // queda en la sesion, aun sin baja
        em.flush();
        // "otra peticion" programa la baja de SELF despues de que esta cargara la entidad
        Instant original = Instant.parse("2026-12-01T00:00:00Z");
        jdbc.update("update users set deletion_scheduled_at = ?, deletion_requested_by = 'SELF' where id = ?",
                java.sql.Timestamp.from(original), id);
        assertThat(loaded.isPendingDeletion()).isFalse(); // la copia en memoria esta desfasada

        service.scheduleByAdmin(adminId, id);

        assertThat(events.stream(AccountDeletionScheduledEvent.class).count()).isZero();
        assertThat(loaded.getDeletionRequestedBy()).isEqualTo(DeletionRequester.SELF);
        assertThat(loaded.getDeletionScheduledAt()).isEqualTo(original);
    }

    @Test
    void anAdminUpdateFromAStaleEntityKeepsAConcurrentDeletion() throws Exception {
        long adminId = userId(login("admin", "Admin12345!"));
        long id = userId(register("stale", "stale@test.com")[0]);
        User loaded = userRepository.findWithRolesById(id).orElseThrow(); // la peticion del admin ya la cargo
        em.flush();
        // "otra peticion" programa la baja entre la carga y el flush
        Instant scheduled = Instant.parse("2026-12-01T00:00:00Z");
        jdbc.update("update users set deletion_scheduled_at = ?, deletion_requested_by = 'SELF' where id = ?",
                java.sql.Timestamp.from(scheduled), id);
        assertThat(loaded.isPendingDeletion()).isFalse();

        userService.setEnabled(adminId, id, false);
        em.flush();

        // el UPDATE solo escribe la columna cambiada: la baja de la otra transaccion se conserva
        assertThat(jdbc.queryForObject("select deletion_scheduled_at from users where id = ?", java.sql.Timestamp.class, id)
                .toInstant()).isEqualTo(scheduled);
        assertThat(jdbc.queryForObject("select deletion_requested_by from users where id = ?", String.class, id))
                .isEqualTo("SELF");
        assertThat(jdbc.queryForObject("select enabled from users where id = ?", Boolean.class, id)).isFalse();
    }

    @Test
    void adminMutationsOnADeletedAccountAre404() throws Exception {
        String admin = login("admin", "Admin12345!");
        long id = userId(register("borrada", "borrada@test.com")[0]);
        em.flush();
        jdbc.update("update users set deleted_at = CURRENT_TIMESTAMP, enabled = false where id = ?", id);
        em.clear(); // la fila cambiada por SQL tiene que leerse de la BD

        mvc.perform(bearer(patch("/api/admin/users/" + id + "/enabled"), admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("notFound.user"));
        mvc.perform(bearer(put("/api/admin/users/" + id + "/roles"), admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"roles\":[\"ROLE_DEVELOPER\"]}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("notFound.user"));
        mvc.perform(bearer(post("/api/admin/users/" + id + "/verify-email"), admin))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("notFound.user"));
        // el usuario se comprueba antes que el juego: sin el arreglo saldria notFound.game
        mvc.perform(bearer(post("/api/admin/users/" + id + "/library/999999"), admin))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("notFound.user"));
        assertThat(jdbc.queryForObject("select enabled from users where id = ?", Boolean.class, id)).isFalse();
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
