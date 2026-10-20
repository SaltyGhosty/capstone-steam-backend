package com.capstone.steam.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.steam.notification.event.UserRegisteredEvent;
import com.capstone.steam.notification.event.VerificationEmailEvent;
import com.jayway.jsonpath.JsonPath;

/** Flujo completo de la verificación de email con el interruptor activado (en los demás tests va desactivado). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@RecordApplicationEvents
@TestPropertySource(properties = "app.auth.email-verification.required=true")
@Import(EmailVerificationIntegrationTest.ClockConfig.class)
class EmailVerificationIntegrationTest {

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
        MutableClock verificationTestClock() {
            return new MutableClock();
        }
    }

    @Autowired private MockMvc mvc;
    @Autowired private MutableClock clock;
    @Autowired private ApplicationEvents events;
    @Autowired private JdbcTemplate jdbc;

    // ------------------------------------------------------------------ registro y login

    @Test
    void registerReturnsNoTokensAndRequiresVerification() throws Exception {
        register("ana", "ana@test.com")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.verificationRequired").value(true))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.email").value("ana@test.com"))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens", Integer.class)).isZero();
        assertThat(lastToken()).hasSize(43);
    }

    @Test
    void theTokenIsStoredOnlyAsAHash() throws Exception {
        register("ana", "ana@test.com").andExpect(status().isCreated());

        String stored = jdbc.queryForObject("SELECT token_hash FROM email_verification_tokens", String.class);
        assertThat(stored).hasSize(64).isEqualTo(TokenHashing.sha256Hex(lastToken())).isNotEqualTo(lastToken());
    }

    @Test
    void loginBeforeVerifyingIsForbiddenOnlyWithTheRightPassword() throws Exception {
        register("ana", "ana@test.com").andExpect(status().isCreated());

        login("ana", "password123").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth.emailNotVerified"))
                .andExpect(jsonPath("$.detail").value("Tu email no está verificado. Revisa tu correo o pide otro enlace."));
        login("ana@test.com", "password123").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth.emailNotVerified"));
        login("ana", "otraClave999").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens", Integer.class)).isZero();
    }

    @Test
    void verifyEmailLogsTheUserIn() throws Exception {
        register("ana", "ana@test.com").andExpect(status().isCreated());

        String body = verify(lastToken()).andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.user.emailVerified").value(true))
                .andReturn().getResponse().getContentAsString();
        String access = JsonPath.read(body, "$.accessToken");

        mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerified").value(true));
        login("ana", "password123").andExpect(status().isOk());
    }

    @Test
    void verificationTokenIsSingleUse() throws Exception {
        register("ana", "ana@test.com").andExpect(status().isCreated());
        String token = lastToken();

        verify(token).andExpect(status().isOk());
        verify(token).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("El enlace no es válido o ha caducado."));
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        register("ana", "ana@test.com").andExpect(status().isCreated());
        clock.now = clock.now.plus(Duration.ofHours(24)).plusSeconds(1);

        verify(lastToken()).andExpect(status().isBadRequest());
        login("ana", "password123").andExpect(status().isForbidden());
    }

    @Test
    void unknownAndBlankTokensAreRejected() throws Exception {
        verify("no-existe").andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ cuentas sin verificar

    @Test
    void registeringAgainWithTheSameUnverifiedEmailReplacesTheAccount() throws Exception {
        long firstId = id(register("ana", "ana@test.com").andExpect(status().isCreated()));
        String firstToken = lastToken();

        long secondId = id(register("ana2", "Ana@Test.com", "otra-clave-123", "DEVELOPER")
                .andExpect(status().isCreated()));
        String secondToken = lastToken();

        assertThat(secondId).isEqualTo(firstId);
        assertThat(secondToken).isNotEqualTo(firstToken);
        verify(firstToken).andExpect(status().isBadRequest());
        verify(secondToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value("ana2"))
                .andExpect(jsonPath("$.user.roles[0]").value("ROLE_DEVELOPER"));
        login("ana2", "otra-clave-123").andExpect(status().isOk());
        login("ana2", "password123").andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email = 'ana@test.com'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void verifiedAccountsAreNeverTouched() throws Exception {
        register("ana", "ana@test.com").andExpect(status().isCreated());
        verify(lastToken()).andExpect(status().isOk());

        register("otra", "ana@test.com").andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("El email ya está registrado"));
        register("ana", "otra@test.com").andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("El nombre de usuario ya está en uso"));
        login("ana", "password123").andExpect(status().isOk());
    }

    @Test
    void usernameOfAnUnverifiedAccountIsFreedOnlyAfterItsLinkExpires() throws Exception {
        register("ana", "ana@test.com").andExpect(status().isCreated());

        register("ana", "intruso@test.com").andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("El nombre de usuario ya está en uso"));

        clock.now = clock.now.plus(Duration.ofHours(24)).plusSeconds(1);
        register("ana", "intruso@test.com").andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email = 'ana@test.com'", Integer.class))
                .isZero();
        verify(lastToken()).andExpect(status().isOk()).andExpect(jsonPath("$.user.email").value("intruso@test.com"));
    }

    @Test
    void disabledAndUnverifiedAccountKeepsTheDisabledResponse() throws Exception {
        long id = id(register("ana", "ana@test.com").andExpect(status().isCreated()));
        String admin = JsonPath.read(login("admin", "Admin12345!").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.accessToken");

        mvc.perform(patch("/api/admin/users/" + id + "/enabled").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk());

        login("ana", "password123").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("La cuenta está desactivada"))
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    // ------------------------------------------------------------------ reenvío

    @Test
    void resendAlwaysReturns204() throws Exception {
        register("pendiente", "pendiente@test.com").andExpect(status().isCreated());
        register("hecha", "hecha@test.com").andExpect(status().isCreated());
        verify(lastToken()).andExpect(status().isOk());
        events.clear();

        resend("nadie@test.com").andExpect(status().isNoContent());
        resend("hecha@test.com").andExpect(status().isNoContent());
        assertThat(events.stream(VerificationEmailEvent.class).count()).isZero();

        resend("pendiente@test.com").andExpect(status().isNoContent());
        VerificationEmailEvent event = events.stream(VerificationEmailEvent.class).findFirst().orElseThrow();
        assertThat(event.email()).isEqualTo("pendiente@test.com");
        assertThat(event.verificationToken()).hasSize(43);
    }

    @Test
    void resendRejectsAMalformedEmail() throws Exception {
        resend("no-es-un-email").andExpect(status().isBadRequest());
        resend("").andExpect(status().isBadRequest());
    }

    @Test
    void resendInvalidatesThePreviousLink() throws Exception {
        register("ana", "ana.reenvio@test.com").andExpect(status().isCreated());
        String first = lastToken();

        resend("ana.reenvio@test.com").andExpect(status().isNoContent());
        String second = events.stream(VerificationEmailEvent.class).reduce((a, b) -> b).orElseThrow()
                .verificationToken();

        assertThat(second).isNotEqualTo(first);
        verify(first).andExpect(status().isBadRequest());
        verify(second).andExpect(status().isOk());
    }

    @Test
    void resendNormalizesTheEmail() throws Exception {
        register("ana", "ana.norm@test.com").andExpect(status().isCreated());

        resend(" Ana.Norm@Test.com ").andExpect(status().isNoContent());
        resend("ANA.NORM@test.com").andExpect(status().isNoContent());
        resend("ana.norm@test.com").andExpect(status().isNoContent());
        // las tres variantes cuentan como el mismo email
        resend("  ana.norm@TEST.com").andExpect(status().isTooManyRequests());
        assertThat(events.stream(VerificationEmailEvent.class).count()).isEqualTo(3);
    }

    @Test
    void fourthResendInAnHourIs429() throws Exception {
        register("ana", "ana.limite@test.com").andExpect(status().isCreated());

        for (int i = 0; i < 3; i++) {
            resend("ana.limite@test.com").andExpect(status().isNoContent());
        }
        resend("ana.limite@test.com").andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.detail").value("Has pedido demasiados correos. Inténtalo dentro de una hora."))
                .andExpect(jsonPath("$.code").value("auth.resendRateLimited"));
        // el 429 también sale para un email que no existe: no delata nada
        for (int i = 0; i < 3; i++) {
            resend("fantasma@test.com").andExpect(status().isNoContent());
        }
        resend("fantasma@test.com").andExpect(status().isTooManyRequests());

        clock.now = clock.now.plus(Duration.ofHours(1)).plusSeconds(1);
        resend("ana.limite@test.com").andExpect(status().isNoContent());
    }

    // ------------------------------------------------------------------ verificación por el admin

    @Test
    void adminCanVerifyAnAccount() throws Exception {
        long id = id(register("ana", "ana.admin@test.com").andExpect(status().isCreated()));
        login("ana", "password123").andExpect(status().isForbidden());

        mvc.perform(post("/api/admin/users/" + id + "/verify-email").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerified").value(true));
        // idempotente
        mvc.perform(post("/api/admin/users/" + id + "/verify-email").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin()))
                .andExpect(status().isOk());

        login("ana", "password123").andExpect(status().isOk());
        mvc.perform(post("/api/admin/users/999999/verify-email").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void nonAdminCannotVerify() throws Exception {
        long id = id(register("ana", "ana.sinadmin@test.com").andExpect(status().isCreated()));
        mvc.perform(post("/api/admin/users/" + id + "/verify-email")).andExpect(status().isUnauthorized());

        register("jugador", "jugador@test.com").andExpect(status().isCreated());
        String token = JsonPath.read(verify(lastToken()).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.accessToken");
        mvc.perform(post("/api/admin/users/" + id + "/verify-email").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
        login("ana", "password123").andExpect(status().isForbidden()); // sigue sin verificar
    }

    // ------------------------------------------------------------------ utilidades

    private String admin() throws Exception {
        return JsonPath.read(login("admin", "Admin12345!").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.accessToken");
    }

    private ResultActions resend(String email) throws Exception {
        return mvc.perform(post("/api/auth/resend-verification").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\"}".formatted(email)));
    }

    private ResultActions register(String username, String email) throws Exception {
        return register(username, email, "password123", "PLAYER");
    }

    private ResultActions register(String username, String email, String password, String accountType)
            throws Exception {
        return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"username":"%s","email":"%s","password":"%s","accountType":"%s"}"""
                .formatted(username, email, password, accountType)));
    }

    private ResultActions login(String login, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"%s\",\"password\":\"%s\"}".formatted(login, password)));
    }

    private ResultActions verify(String token) throws Exception {
        return mvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\"}".formatted(token)));
    }

    private static long id(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    /** El token en claro del último correo de registro (el evento que recoge el listener de correo). */
    private String lastToken() {
        return events.stream(UserRegisteredEvent.class).reduce((first, second) -> second).orElseThrow()
                .verificationToken();
    }
}
