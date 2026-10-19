package com.capstone.steam.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.steam.user.User;
import com.capstone.steam.user.UserRepository;

/** Purga de refresh tokens: solo se borran familias enteras cuyos tokens han caducado todos (D48). */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RefreshTokenPurgeIntegrationTest {

    private static final Duration GRACE = Duration.ofDays(1);

    @Autowired
    private RefreshTokenService service;

    @Autowired
    private RefreshTokenRepository repository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private Long userId;
    private int seq;

    @BeforeEach
    void setUp() {
        userId = userRepository.save(new User("purger", "purger@test.com", "hash")).getId();
    }

    private void token(String family, Instant expiresAt, boolean revoked) {
        RefreshToken t = repository.saveAndFlush(
                new RefreshToken(userId, "hash-" + family + "-" + seq++, family, expiresAt));
        if (revoked) {
            repository.revokeIfActive(t.getId(), Instant.now());
        }
    }

    private int countFamily(String family) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens WHERE family_id = ?", Integer.class, family);
    }

    @Test
    void deletesAFamilyWhoseTokensAllExpiredBeforeTheCutoff() {
        Instant old = Instant.now().minus(Duration.ofDays(3));
        token("fam-old", old.minus(Duration.ofDays(1)), true);
        token("fam-old", old, true);

        assertThat(service.purgeExpired(GRACE)).isEqualTo(2);
        assertThat(countFamily("fam-old")).isZero();
    }

    @Test
    void keepsTheWholeFamilyWhileAnyTokenIsStillValid() {
        // el token viejo está revocado y caducado, pero el nuevo sigue vivo: reutilizar el viejo debe poder tirar la familia
        token("fam-live", Instant.now().minus(Duration.ofDays(5)), true);
        token("fam-live", Instant.now().plus(Duration.ofDays(2)), false);

        assertThat(service.purgeExpired(GRACE)).isZero();
        assertThat(countFamily("fam-live")).isEqualTo(2);
    }

    @Test
    void keepsFamiliesExpiredInsideTheGraceMargin() {
        token("fam-grace", Instant.now().minus(Duration.ofHours(2)), false);

        assertThat(service.purgeExpired(GRACE)).isZero();
        assertThat(countFamily("fam-grace")).isEqualTo(1);
    }

    @Test
    void doesNotTouchUnexpiredTokensWhilePurgingOthers() {
        token("fam-valid", Instant.now().plus(Duration.ofDays(6)), false);
        token("fam-dead", Instant.now().minus(Duration.ofDays(4)), false);

        assertThat(service.purgeExpired(GRACE)).isEqualTo(1);
        assertThat(countFamily("fam-valid")).isEqualTo(1);
        assertThat(countFamily("fam-dead")).isZero();
    }
}
