package com.capstone.steam.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import com.capstone.steam.auth.dto.AccountType;
import com.capstone.steam.auth.dto.RegisterRequest;
import com.capstone.steam.common.exception.BadRequestException;
import com.capstone.steam.notification.event.UserRegisteredEvent;

/**
 * Registro y verificación a la vez sobre la misma cuenta, con hilos y commits reales (por eso usa su propio
 * H2): las filas de {@code users} se leen con bloqueo pesimista y el enlace se gasta con un UPDATE condicional.
 */
@SpringBootTest
@ActiveProfiles("test")
@RecordApplicationEvents
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:verification_concurrency;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "app.auth.email-verification.required=true"})
class EmailVerificationConcurrencyTest {

    @Autowired private AuthService authService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ApplicationEvents events;

    private static RegisterRequest request(String username, String email, String password) {
        return new RegisterRequest(username, email, password, null, AccountType.PLAYER, null);
    }

    private String tokenOf(String username) {
        return events.stream(UserRegisteredEvent.class).filter(e -> username.equals(e.username()))
                .reduce((first, second) -> second).orElseThrow().verificationToken();
    }

    private <T> List<Object> runTogether(List<Callable<T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        for (Callable<T> task : tasks) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                try {
                    return task.call();
                } catch (RuntimeException e) {
                    return e;
                }
            }));
        }
        ready.await();
        go.countDown();
        List<Object> results = new ArrayList<>();
        for (Future<Object> future : futures) {
            results.add(future.get());
        }
        pool.shutdown();
        return results;
    }

    @Test
    void verifyingWhileSomeoneRegistersWithTheSameEmailLeavesTheVictimsData() throws Exception {
        authService.register(request("victima", "carrera1@test.com", "ClaveVictima1"));
        String victimLink = tokenOf("victima");

        List<Object> results = runTogether(List.of(
                () -> authService.verifyEmail(victimLink),
                () -> authService.register(request("atacante", "carrera1@test.com", "ClaveAtacante1"))));

        assertThat(results.get(0)).isNotInstanceOf(Exception.class); // la víctima entra
        // el registro del atacante da 409 (si la cuenta ya estaba verificada) o 201 (un enlace más): da igual
        String username = jdbc.queryForObject("SELECT username FROM users WHERE email = 'carrera1@test.com'", String.class);
        String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE email = 'carrera1@test.com'", String.class);
        assertThat(username).isEqualTo("victima");
        assertThat(passwordEncoder.matches("ClaveVictima1", hash)).isTrue();
        assertThat(passwordEncoder.matches("ClaveAtacante1", hash)).isFalse();
        assertThat(jdbc.queryForObject("SELECT email_verified FROM users WHERE email = 'carrera1@test.com'", Boolean.class)).isTrue();
    }

    @Test
    void twoLinksOfTheSameAccountPressedAtOnceAreNotBothAccepted() throws Exception {
        authService.register(request("primera", "carrera2@test.com", "ClavePrimera11"));
        String first = tokenOf("primera");
        authService.register(request("segunda", "carrera2@test.com", "ClaveSegunda11"));
        String second = tokenOf("segunda");

        List<Object> results = runTogether(List.of(
                () -> authService.verifyEmail(first),
                () -> authService.verifyEmail(second)));

        long accepted = results.stream().filter(r -> !(r instanceof Exception)).count();
        long rejected = results.stream().filter(r -> r instanceof BadRequestException).count();
        assertThat(accepted).isEqualTo(1);
        assertThat(rejected).isEqualTo(1);
        // la cuenta tiene los datos de UN solo enlace, completos
        String username = jdbc.queryForObject("SELECT username FROM users WHERE email = 'carrera2@test.com'", String.class);
        String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE email = 'carrera2@test.com'", String.class);
        String password = "primera".equals(username) ? "ClavePrimera11" : "ClaveSegunda11";
        assertThat(passwordEncoder.matches(password, hash)).isTrue();
    }
}
