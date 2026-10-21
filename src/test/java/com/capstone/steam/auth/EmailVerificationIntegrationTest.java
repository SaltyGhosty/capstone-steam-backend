package com.capstone.steam.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import java.util.List;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
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
    @Autowired private ResendRateLimiter limiter;
    @Autowired private EntityManager em;

    /** Reloj y límite de reenvíos son compartidos por todos los tests de la clase: cada test parte de cero. */
    @BeforeEach
    void resetSharedState() {
        clock.now = Instant.now();
        limiter.clear();
    }

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
    void attackerCannotTakeOverAnUnverifiedEmail() throws Exception {
        long victimId = id(register("victima", "v@test.com", "ClaveVictima1", "PLAYER").andExpect(status().isCreated()));
        String victimLink = lastToken();

        // el atacante se registra con el email de la víctima y otra contraseña: 201 igual que un registro nuevo
        long attackerId = id(register("atacante", "v@test.com", "ClaveAtacante1", "DEVELOPER").andExpect(status().isCreated()));
        String attackerLink = lastToken();
        assertThat(attackerId).isEqualTo(victimId);
        assertThat(attackerLink).isNotEqualTo(victimLink);
        // la fila no se tocó
        assertThat(jdbc.queryForObject("SELECT username FROM users WHERE id = ?", String.class, victimId))
                .isEqualTo("victima");

        // la víctima pulsa SU enlace: la cuenta queda con SUS datos
        verify(victimLink, "ClaveVictima1").andExpect(status().isOk()).andExpect(jsonPath("$.user.username").value("victima"))
                .andExpect(jsonPath("$.user.roles.length()").value(1));
        login("victima", "ClaveVictima1").andExpect(status().isOk());
        login("victima", "ClaveAtacante1").andExpect(status().isUnauthorized());
        login("atacante", "ClaveAtacante1").andExpect(status().isUnauthorized());
        // el enlace del atacante ya no vale
        verify(attackerLink).andExpect(status().isBadRequest());
    }

    @Test
    void verifyRequiresThePasswordOfThatRegistration() throws Exception {
        // la víctima y el atacante piden el MISMO usuario con el mismo email: los dos correos nombran «juanp»
        register("juanp", "v@test.com", "ClaveVictima1", "PLAYER").andExpect(status().isCreated());
        String victimLink = lastToken();
        register("juanp", "v@test.com", "ClaveAtacante1", "PLAYER").andExpect(status().isCreated());
        String attackerLink = lastToken();

        // pulsar el enlace del atacante con la contraseña de la víctima no vale (ni a la inversa)
        verify(attackerLink, "ClaveVictima1").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.verificationPasswordInvalid"))
                .andExpect(jsonPath("$.detail").value("La contraseña no coincide con la de este registro."));
        verify(victimLink, "ClaveAtacante1").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.verificationPasswordInvalid"));
        login("juanp", "ClaveVictima1").andExpect(status().isForbidden()); // sigue sin verificar

        verify(victimLink, "ClaveVictima1").andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isNotEmpty());
        login("juanp", "ClaveVictima1").andExpect(status().isOk());
        login("juanp", "ClaveAtacante1").andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordDoesNotConsumeTheLink() throws Exception {
        register("ana", "ana@test.com").andExpect(status().isCreated());
        String link = lastToken();

        verify(link, "equivocada1").andExpect(status().isBadRequest());
        verify(link, "equivocada2").andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject("SELECT failed_attempts FROM email_verification_tokens WHERE token_hash = ?",
                Integer.class, TokenHashing.sha256Hex(link))).isEqualTo(2);
        verify(link).andExpect(status().isOk());
    }

    @Test
    void fiveWrongPasswordsInvalidateTheLink() throws Exception {
        register("ana", "ana@test.com").andExpect(status().isCreated());
        String link = lastToken();

        for (int i = 0; i < 5; i++) {
            verify(link, "equivocada" + i).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("auth.verificationPasswordInvalid"));
        }
        // anulado: ni con la contraseña buena, y el aviso pasa a «enlace no válido»
        verify(link).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("auth.verificationInvalid"));
        preview(link).andExpect(status().isBadRequest());
        login("ana", "password123").andExpect(status().isForbidden());
    }

    @Test
    void previewShowsTheUsernameOnlyForAValidLink() throws Exception {
        register("ana", "ana@test.com").andExpect(status().isCreated());
        String link = lastToken();

        preview(link).andExpect(status().isOk()).andExpect(jsonPath("$.username").value("ana"))
                .andExpect(jsonPath("$.email").doesNotExist());
        preview(link).andExpect(status().isOk()); // idempotente: no gasta el enlace
        preview("no-existe").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.verificationInvalid"));

        verify(link).andExpect(status().isOk());
        preview(link).andExpect(status().isBadRequest()); // usado

        register("leo", "leo@test.com").andExpect(status().isCreated());
        String expiring = lastToken();
        clock.now = clock.now.plus(Duration.ofHours(24)).plusSeconds(1);
        preview(expiring).andExpect(status().isBadRequest()); // caducado
    }

    @Test
    void resendSendsAllPendingLinks() throws Exception {
        register("juanp", "v@test.com", "ClaveVictima1", "PLAYER").andExpect(status().isCreated());
        register("juanp", "v@test.com", "ClaveAtacante1", "PLAYER").andExpect(status().isCreated());
        events.clear();

        resend("v@test.com").andExpect(status().isNoContent());

        List<VerificationEmailEvent> sent = events.stream(VerificationEmailEvent.class).toList();
        assertThat(sent).hasSize(2); // mismo usuario, dos registros distintos: dos correos
        // cada enlace nuevo conserva la contraseña de su registro
        verify(sent.get(0).verificationToken(), "ClaveAtacante1").andExpect(status().isOk());
    }

    @Test
    void adminCannotVerifyByHandWhenSeveralRegistrationsArePending() throws Exception {
        long id = id(register("victima", "v@test.com", "ClaveVictima1", "PLAYER").andExpect(status().isCreated()));
        register("atacante", "v@test.com", "ClaveAtacante1", "PLAYER").andExpect(status().isCreated());

        mvc.perform(post("/api/admin/users/" + id + "/verify-email").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("auth.verificationAmbiguous"));
        assertThat(jdbc.queryForObject("SELECT username FROM users WHERE id = ?", String.class, id)).isEqualTo("victima");
        login("victima", "ClaveVictima1").andExpect(status().isForbidden()); // sigue sin verificar
    }

    @Test
    void adminVerifyConsidersAnnulledLinksAndNeverAppliesTheWrongRegistration() throws Exception {
        long id = id(register("juanp", "v@test.com", "ClaveVictima1", "PLAYER").andExpect(status().isCreated()));
        register("juanp", "v@test.com", "ClaveAtacante1", "PLAYER").andExpect(status().isCreated());
        String attackerLink = lastToken();
        // la víctima teclea 5 veces su contraseña en el enlace del atacante: ese enlace se anula
        for (int i = 0; i < 5; i++) {
            verify(attackerLink, "ClaveVictima1").andExpect(status().isBadRequest());
        }
        // sigue habiendo 2 registros distintos (el anulado cuenta): el admin no puede elegir, y no aplica ninguno
        mvc.perform(post("/api/admin/users/" + id + "/verify-email").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("auth.verificationAmbiguous"));
        login("juanp", "ClaveVictima1").andExpect(status().isForbidden());
    }

    @Test
    void adminVerifyAppliesTheOnlyRegistrationEvenIfItsLinkExpired() throws Exception {
        long id = id(register("solo", "solo@test.com", "ClaveSolo1234", "PLAYER").andExpect(status().isCreated()));
        clock.now = clock.now.plus(Duration.ofHours(25));

        mvc.perform(post("/api/admin/users/" + id + "/verify-email").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("solo"));
        login("solo", "ClaveSolo1234").andExpect(status().isOk());
    }

    @Test
    void resendInheritsTheFailedAttemptsOfTheRegistration() throws Exception {
        register("ana", "ana@test.com").andExpect(status().isCreated());
        String link = lastToken();
        for (int i = 0; i < 3; i++) {
            verify(link, "equivocada" + i).andExpect(status().isBadRequest());
        }

        resend("ana@test.com").andExpect(status().isNoContent());
        String newLink = events.stream(VerificationEmailEvent.class).reduce((a, b) -> b).orElseThrow().verificationToken();

        assertThat(jdbc.queryForObject("SELECT failed_attempts FROM email_verification_tokens WHERE token_hash = ?",
                Integer.class, TokenHashing.sha256Hex(newLink))).isEqualTo(3);
        // al nuevo le quedan 2 intentos
        verify(newLink, "mala1").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("auth.verificationPasswordInvalid"));
        verify(newLink, "mala2").andExpect(status().isBadRequest());
        verify(newLink).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("auth.verificationInvalid"));
    }

    @Test
    void resendDoesNotReissueARegistrationWhoseLinkWasAnnulledByFailures() throws Exception {
        register("ana", "ana@test.com").andExpect(status().isCreated());
        String link = lastToken();
        for (int i = 0; i < 5; i++) {
            verify(link, "equivocada" + i).andExpect(status().isBadRequest());
        }
        events.clear();

        resend("ana@test.com").andExpect(status().isNoContent());

        assertThat(events.stream(VerificationEmailEvent.class).count()).isZero();
        // la vía es registrarse de nuevo
        register("ana", "ana@test.com").andExpect(status().isCreated());
        verify(lastToken()).andExpect(status().isOk());
    }

    @Test
    void attemptsAreCountedPerRegistrationNotPerLink() throws Exception {
        register("ana", "ana@test.com").andExpect(status().isCreated());
        String link = lastToken();
        for (int i = 0; i < 3; i++) {
            verify(link, "equivocada" + i).andExpect(status().isBadRequest());
        }
        resend("ana@test.com").andExpect(status().isNoContent());
        String newLink = events.stream(VerificationEmailEvent.class).reduce((a, b) -> b).orElseThrow().verificationToken();

        // 2 intentos más en el enlace ORIGINAL agotan los 5 del registro: el enlace nuevo también queda anulado
        verify(link, "mala1").andExpect(status().isBadRequest());
        verify(link, "mala2").andExpect(status().isBadRequest());
        verify(newLink).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("auth.verificationInvalid"));
    }

    @Test
    void adminUserListCanBeFilteredByUsernameOrEmail() throws Exception {
        register("buscame", "buscame@test.com").andExpect(status().isCreated());
        register("otra", "otra@test.com").andExpect(status().isCreated());

        mvc.perform(get("/api/admin/users").param("q", "BUSCAM").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].username").value("buscame"))
                .andExpect(jsonPath("$.content[0].emailVerified").value(false));
        mvc.perform(get("/api/admin/users").param("q", "otra@test").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin()))
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void conflictingRegistrationsDoNotSpendTheEmailsQuota() throws Exception {
        register("ocupado", "ocupado@test.com").andExpect(status().isCreated());
        verify(lastToken()).andExpect(status().isOk());

        for (int i = 0; i < 5; i++) {
            register("ocupado", "nuevo@test.com").andExpect(status().isConflict()); // usuario de una cuenta verificada
        }
        // el cupo de nuevo@ sigue entero: 3 registros aceptados y el 4.º, 429
        for (int i = 0; i < 3; i++) {
            register("libre" + i, "nuevo@test.com").andExpect(status().isCreated());
        }
        register("libre3", "nuevo@test.com").andExpect(status().isTooManyRequests());
    }

    @Test
    void registeringAgainWithTheSameUnverifiedEmailKeepsTheFirstLinkValid() throws Exception {
        long firstId = id(register("ana", "ana@test.com").andExpect(status().isCreated()));
        String firstLink = lastToken();

        long secondId = id(register("ana2", "ana@test.com", "otra-clave-123", "DEVELOPER").andExpect(status().isCreated()));

        assertThat(secondId).isEqualTo(firstId);
        verify(firstLink).andExpect(status().isOk()).andExpect(jsonPath("$.user.username").value("ana"));
        login("ana", "password123").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email = 'ana@test.com'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void theLinkAppliesItsOwnDataAndVerifyingOneLinkInvalidatesTheOthers() throws Exception {
        register("ana", "ana@test.com", "password123", "PLAYER").andExpect(status().isCreated());
        String first = lastToken();
        register("ana2", "ana@test.com", "otra-clave-123", "DEVELOPER").andExpect(status().isCreated());
        String second = lastToken();

        verify(second, "otra-clave-123").andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value("ana2"))
                .andExpect(jsonPath("$.user.roles[0]").value("ROLE_DEVELOPER"));
        login("ana2", "otra-clave-123").andExpect(status().isOk());
        login("ana2", "password123").andExpect(status().isUnauthorized());
        verify(first).andExpect(status().isBadRequest());
    }

    @Test
    void verifyFailsWith409IfUsernameWasTakenMeanwhile() throws Exception {
        register("ana", "ana@test.com").andExpect(status().isCreated());
        register("ana2", "ana@test.com").andExpect(status().isCreated());
        String linkForAna2 = lastToken();
        // otra persona coge «ana2» antes de que se pulse el enlace
        register("ana2", "otra@test.com").andExpect(status().isCreated());

        verify(linkForAna2).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("El nombre de usuario ya está en uso"));
        // el enlace quedó sin usar y la cuenta sigue sin verificar
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM email_verification_tokens WHERE token_hash = ? AND used_at IS NULL",
                Integer.class, TokenHashing.sha256Hex(linkForAna2))).isEqualTo(1);
        login("ana", "password123").andExpect(status().isForbidden());
    }

    @Test
    void reRegistrationIsRateLimited() throws Exception {
        for (int i = 0; i < 3; i++) {
            register("ana" + i, "ana@test.com").andExpect(status().isCreated());
        }
        register("ana3", "ana@test.com").andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("auth.resendRateLimited"));

        clock.now = clock.now.plus(Duration.ofHours(1)).plusSeconds(1);
        register("ana3", "ana@test.com").andExpect(status().isCreated());
    }

    @Test
    void resendSendsOneLinkPerPendingRegistration() throws Exception {
        register("victima", "v@test.com", "ClaveVictima1", "PLAYER").andExpect(status().isCreated());
        register("atacante", "v@test.com", "ClaveAtacante1", "PLAYER").andExpect(status().isCreated());
        events.clear();

        resend("v@test.com").andExpect(status().isNoContent());

        List<VerificationEmailEvent> sent = events.stream(VerificationEmailEvent.class).toList();
        assertThat(sent).extracting(VerificationEmailEvent::username).containsExactlyInAnyOrder("victima", "atacante");
        String victimLink = sent.stream().filter(e -> e.username().equals("victima")).findFirst().orElseThrow()
                .verificationToken();
        verify(victimLink, "ClaveVictima1").andExpect(status().isOk()).andExpect(jsonPath("$.user.username").value("victima"));
        login("victima", "ClaveVictima1").andExpect(status().isOk());
        login("victima", "ClaveAtacante1").andExpect(status().isUnauthorized());
    }

    @Test
    void resendWithOnlyExpiredLinksRepeatsTheLatestOne() throws Exception {
        register("ana", "ana@test.com").andExpect(status().isCreated());
        clock.now = clock.now.plus(Duration.ofHours(25));
        events.clear();

        resend("ana@test.com").andExpect(status().isNoContent());

        assertThat(events.stream(VerificationEmailEvent.class).map(VerificationEmailEvent::username).toList())
                .containsExactly("ana");
    }

    @Test
    void resendSendsAtMostThreeLinksAndSpendsOneUnitOfTheLimit() throws Exception {
        register("ana1", "ana@test.com").andExpect(status().isCreated());
        clock.now = clock.now.plusSeconds(1);
        register("ana2", "ana@test.com").andExpect(status().isCreated());
        // dos registros gastaron 2 del cupo; añade otros 2 con el cupo renovado
        clock.now = clock.now.plus(Duration.ofHours(1)).plusSeconds(1);
        register("ana3", "ana@test.com").andExpect(status().isCreated());
        register("ana4", "ana@test.com").andExpect(status().isCreated());
        events.clear();

        resend("ana@test.com").andExpect(status().isNoContent()); // 3.º del cupo: manda 3 correos (a4, a3, a2)
        assertThat(events.stream(VerificationEmailEvent.class).count()).isEqualTo(3);
        resend("ana@test.com").andExpect(status().isTooManyRequests());
    }

    @Test
    void verifyingADisabledAccountVerifiesItButGivesNoSession() throws Exception {
        long id = id(register("ana", "ana@test.com").andExpect(status().isCreated()));
        String link = lastToken();
        mvc.perform(patch("/api/admin/users/" + id + "/enabled").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk());

        verify(link).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("La cuenta está desactivada"))
                .andExpect(jsonPath("$.accessToken").doesNotExist());

        em.flush();
        assertThat(jdbc.queryForObject("SELECT email_verified FROM users WHERE id = ?", Boolean.class, id)).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ?", Integer.class, id)).isZero();
        login("ana", "password123").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("La cuenta está desactivada"));
    }

    @Test
    void verifyingAnAccountPendingDeletionVerifiesItButGivesNoSession() throws Exception {
        long id = id(register("ana", "ana@test.com").andExpect(status().isCreated()));
        String link = lastToken();
        // el admin programa la baja de la cuenta sin verificar: el enlace del correo sigue siendo válido
        mvc.perform(delete("/api/admin/users/" + id).header(HttpHeaders.AUTHORIZATION, "Bearer " + admin()))
                .andExpect(status().isOk());

        verify(link).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth.accountDeletionByAdmin"))
                .andExpect(jsonPath("$.deletionScheduledAt").exists())
                .andExpect(jsonPath("$.accessToken").doesNotExist());

        em.flush();
        assertThat(jdbc.queryForObject("SELECT email_verified FROM users WHERE id = ?", Boolean.class, id)).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ?", Integer.class, id)).isZero();
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
    void usernameOfAnUnverifiedAccountIsFreedOnlyWhenTheAccountIsOlderThanTheTtl() throws Exception {
        register("ana", "ana@test.com").andExpect(status().isCreated());

        register("ana", "intruso@test.com").andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("El nombre de usuario ya está en uso"));

        // reenviar el enlace no retiene el nombre: cuenta la edad de la cuenta, no la del último enlace
        clock.now = clock.now.plus(Duration.ofHours(23));
        resend("ana@test.com").andExpect(status().isNoContent());
        clock.now = clock.now.plus(Duration.ofHours(1)).plusSeconds(5);
        register("ana", "intruso@test.com").andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email = 'ana@test.com'", Integer.class))
                .isZero();
        verify(lastToken()).andExpect(status().isOk()).andExpect(jsonPath("$.user.email").value("intruso@test.com"));
    }

    @Test
    void anOldUnverifiedAccountWithDataOrExtraRolesKeepsItsUsername() throws Exception {
        long withSession = id(register("conreg", "conreg@test.com").andExpect(status().isCreated()));
        long admin = id(register("conadmin", "conadmin@test.com").andExpect(status().isCreated()));
        jdbc.update("INSERT INTO refresh_tokens (user_id, token_hash, family_id, expires_at) VALUES (?, 'h-reg', 'f', CURRENT_TIMESTAMP)",
                withSession);
        jdbc.update("INSERT INTO user_roles (user_id, role_id) SELECT ?, id FROM roles WHERE name = 'ROLE_ADMIN'", admin);
        em.flush();
        em.clear(); // los roles insertados por SQL tienen que leerse de la BD, no de la caché
        clock.now = clock.now.plus(Duration.ofDays(2));

        register("conreg", "otro1@test.com").andExpect(status().isConflict());
        register("conadmin", "otro2@test.com").andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE username IN ('conreg', 'conadmin')", Integer.class))
                .isEqualTo(2);
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
    void resendIssuesAnotherLinkAndUsingOneInvalidatesTheOther() throws Exception {
        register("ana", "ana.reenvio@test.com").andExpect(status().isCreated());
        String first = lastToken();

        resend("ana.reenvio@test.com").andExpect(status().isNoContent());
        String second = events.stream(VerificationEmailEvent.class).reduce((a, b) -> b).orElseThrow()
                .verificationToken();

        assertThat(second).isNotEqualTo(first);
        verify(second).andExpect(status().isOk());
        verify(first).andExpect(status().isBadRequest());
    }

    @Test
    void resendNormalizesTheEmail() throws Exception {
        register("ana", "ana.norm@test.com").andExpect(status().isCreated());

        // el registro ya gastó un correo del cupo (3 por hora y email)
        resend(" Ana.Norm@Test.com ").andExpect(status().isNoContent());
        resend("ANA.NORM@test.com").andExpect(status().isNoContent());
        // las variantes cuentan como el mismo email
        resend("  ana.norm@TEST.com").andExpect(status().isTooManyRequests());
        assertThat(events.stream(VerificationEmailEvent.class).count()).isEqualTo(2);
    }

    @Test
    void fourthResendInAnHourIs429() throws Exception {
        register("ana", "ana.limite@test.com").andExpect(status().isCreated());

        for (int i = 0; i < 2; i++) { // el registro ya gastó el primero de los 3 correos por hora
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
        return verify(token, "password123");
    }

    private ResultActions verify(String token, String password) throws Exception {
        return mvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\",\"password\":\"%s\"}".formatted(token, password)));
    }

    private ResultActions preview(String token) throws Exception {
        return mvc.perform(post("/api/auth/verify-email/preview").contentType(MediaType.APPLICATION_JSON)
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
