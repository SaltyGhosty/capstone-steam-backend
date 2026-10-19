package com.capstone.steam.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.capstone.steam.common.exception.UnauthorizedException;
import com.capstone.steam.user.User;
import com.capstone.steam.user.UserRepository;

/**
 * Rotación de refresh tokens con transacciones reales (sin {@code @Transactional} en el test):
 * solo un refresh simultáneo con el mismo token puede ganar, y la reutilización deja la familia
 * revocada en la BD. Usa su propio H2 y limpia sus filas.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:refreshconcurrency;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1")
class RefreshTokenConcurrencyTest {

    private static final int THREADS = 8;

    @Autowired private RefreshTokenService service;
    @Autowired private UserRepository users;
    @Autowired private JdbcTemplate jdbc;

    private User user;

    @BeforeEach
    void setUp() {
        String suffix = String.valueOf(System.nanoTime());
        user = users.save(new User("r" + suffix, suffix + "@test.com", "hash"));
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM refresh_tokens WHERE user_id = ?", user.getId());
        jdbc.update("DELETE FROM users WHERE id = ?", user.getId());
    }

    @Test
    void onlyOneOfManySimultaneousRefreshesWinsAndTheFamilyEndsRevoked() throws Exception {
        String raw = service.issue(user);
        CountDownLatch start = new CountDownLatch(1);
        List<Object> outcomes = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(THREADS)) {
            List<Future<Object>> futures = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        return service.rotate(raw);
                    } catch (UnauthorizedException e) {
                        return e;
                    }
                }));
            }
            start.countDown();
            for (Future<Object> f : futures) {
                outcomes.add(f.get());
            }
        }

        assertThat(outcomes).filteredOn(o -> o instanceof RefreshTokenService.Rotation).hasSize(1);
        assertThat(outcomes).filteredOn(o -> o instanceof UnauthorizedException).hasSize(THREADS - 1);
        // los perdedores detectan reutilización y cierran la familia: ningún hijo queda vivo
        assertThat(liveTokens()).isZero();
    }

    @Test
    void reusingARotatedTokenLeavesTheFamilyRevokedInTheDatabase() {
        String first = service.issue(user);
        RefreshTokenService.Rotation second = service.rotate(first);
        assertThat(liveTokens()).isEqualTo(1);

        try {
            service.rotate(first);
        } catch (UnauthorizedException expected) {
            // la reutilización se rechaza
        }

        assertThat(liveTokens()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ?", Integer.class,
                user.getId())).isEqualTo(2);
        assertThat(second.refreshToken()).isNotEqualTo(first);
    }

    private int liveTokens() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ? AND revoked_at IS NULL",
                Integer.class, user.getId());
    }
}
