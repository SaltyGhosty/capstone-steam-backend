package com.capstone.steam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifica que las migraciones V1-V12 se aplican sobre H2 y que las
 * restricciones clave del esquema funcionan.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FlywayMigrationTest {

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void appliesAllMigrationsUpToV12_1() {
        var applied = flyway.info().applied();
        assertThat(applied).hasSize(13);
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("12.1");
    }

    @Test
    void existingUsersAreVerifiedAfterV12() {
        assertThat(jdbc.queryForObject("SELECT email_verified FROM users WHERE username = 'admin'", Boolean.class))
                .isTrue();
    }

    @Test
    void usernamesAreUniqueIgnoringCase() {
        insertUser("JuanP");
        assertThatThrownBy(() -> insertUser("juanp")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void verificationLinksStartWithZeroFailedAttempts() {
        long userId = insertUser("intentos");
        jdbc.update("INSERT INTO email_verification_tokens (user_id, token_hash, expires_at, username, password_hash) "
                + "VALUES (?, 'h-int', CURRENT_TIMESTAMP, 'x', 'y')", userId);
        assertThat(jdbc.queryForObject("SELECT failed_attempts FROM email_verification_tokens WHERE token_hash = 'h-int'",
                Integer.class)).isZero();
    }

    @Test
    void newUsersAreUnverifiedByDefault() {
        long id = insertUser("sinverificar");
        assertThat(jdbc.queryForObject("SELECT email_verified FROM users WHERE id = ?", Boolean.class, id)).isFalse();
    }

    @Test
    void emailVerificationTokenHashIsUniqueAndDiesWithItsUser() {
        long userId = insertUser("verificador");
        String sql = "INSERT INTO email_verification_tokens (user_id, token_hash, expires_at, username, password_hash) "
                + "VALUES (?, 'h1', CURRENT_TIMESTAMP, 'nuevo', 'x')";
        jdbc.update(sql, userId);
        assertThatThrownBy(() -> jdbc.update(sql, userId)).isInstanceOf(DataIntegrityViolationException.class);

        jdbc.update("DELETE FROM users WHERE id = ?", userId);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM email_verification_tokens", Integer.class)).isZero();
    }

    @Test
    void emailVerificationTokensCarryTheRegistrationDataWithAValidLocale() {
        long userId = insertUser("datos");
        jdbc.update("INSERT INTO email_verification_tokens (user_id, token_hash, expires_at, username, password_hash, "
                + "display_name, locale, developer) VALUES (?, 'h2', CURRENT_TIMESTAMP, 'otro', 'hash', 'Otro', 'it', TRUE)",
                userId);
        assertThat(jdbc.queryForObject("SELECT developer FROM email_verification_tokens WHERE token_hash = 'h2'",
                Boolean.class)).isTrue();
        assertThatThrownBy(() -> jdbc.update("INSERT INTO email_verification_tokens (user_id, token_hash, expires_at, "
                + "username, password_hash, locale) VALUES (?, 'h3', CURRENT_TIMESTAMP, 'x', 'y', 'fr')", userId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void friendshipsRejectUnorderedAndDuplicatePairs() {
        long a = insertUser("amiga");
        long b = insertUser("amigo");
        long low = Math.min(a, b);
        long high = Math.max(a, b);
        String sql = "INSERT INTO friendships (requester_id, addressee_id, user_low_id, user_high_id, status) "
                + "VALUES (?, ?, ?, ?, 'PENDING')";

        // low > high no vale
        assertThatThrownBy(() -> jdbc.update(sql, low, high, high, low))
                .isInstanceOf(DataIntegrityViolationException.class);

        jdbc.update(sql, low, high, low, high);
        // el mismo par, en cualquier dirección, ya existe
        assertThatThrownBy(() -> jdbc.update(sql, high, low, low, high))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT profile_public FROM users WHERE id = ?", Boolean.class, a)).isFalse();
    }

    @Test
    void friendshipsPairMustMatchRequesterAndAddressee() {
        // los ids de identidad crecen: x < y < z
        long x = insertUser("par_x");
        long y = insertUser("par_y");
        long z = insertUser("par_z");
        assertThat(x).isLessThan(y);
        assertThat(y).isLessThan(z);
        String sql = "INSERT INTO friendships (requester_id, addressee_id, user_low_id, user_high_id, status) "
                + "VALUES (?, ?, ?, ?, 'PENDING')";

        // low/high ordenados y distintos, pero de otra pareja: solo lo frena ck_friendships_pair
        assertThatThrownBy(() -> jdbc.update(sql, x, y, x, z))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(sql, x, y, y, z))
                .isInstanceOf(DataIntegrityViolationException.class);

        // coherente, con el solicitante como el mayor
        assertThat(jdbc.update(sql, y, x, x, y)).isEqualTo(1);
    }

    @Test
    void refreshTokenHashIsUniqueAndTokensDieWithTheirUser() {
        long userId = insertUser("refresher");
        jdbc.update("""
                INSERT INTO refresh_tokens (user_id, token_hash, family_id, expires_at)
                VALUES (?, 'h1', 'f1', CURRENT_TIMESTAMP)""", userId);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO refresh_tokens (user_id, token_hash, family_id, expires_at)
                VALUES (?, 'h1', 'f2', CURRENT_TIMESTAMP)""", userId))
                .isInstanceOf(DataIntegrityViolationException.class);

        jdbc.update("DELETE FROM users WHERE id = ?", userId);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens", Integer.class)).isZero();
    }

    @Test
    void userLocaleDefaultsToEsAndRejectsUnknownValues() {
        long userId = insertUser("localeuser");
        assertThat(jdbc.queryForObject("SELECT locale FROM users WHERE id = ?", String.class, userId))
                .isEqualTo("es");

        jdbc.update("UPDATE users SET locale = 'it' WHERE id = ?", userId);
        assertThatThrownBy(() -> jdbc.update("UPDATE users SET locale = 'fr' WHERE id = ?", userId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE users SET locale = NULL WHERE id = ?", userId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void seedsRolesAndGenres() {
        assertThat(jdbc.queryForList("SELECT name FROM roles ORDER BY id", String.class))
                .containsExactly("ROLE_USER", "ROLE_DEVELOPER", "ROLE_ADMIN");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM genres", Integer.class)).isEqualTo(12);
    }

    @Test
    void rejectsInvalidDiscountAndReviewLanguage() {
        long devId = insertUser("dev");

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO games (title, slug, developer_id, price, discount_percent)
                VALUES ('Bad', 'bad', ?, 10.00, 150)""", devId))
                .isInstanceOf(DataIntegrityViolationException.class);

        jdbc.update("INSERT INTO games (title, slug, developer_id, price) VALUES ('Ok', 'ok', ?, 10.00)", devId);
        long gameId = jdbc.queryForObject("SELECT id FROM games WHERE slug = 'ok'", Long.class);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO reviews (user_id, game_id, recommended, language) VALUES (?, ?, TRUE, 'fr')", devId, gameId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsMorePositiveThanTotalReviews() {
        long devId = insertUser("dev2");
        jdbc.update("INSERT INTO games (title, slug, developer_id, price) VALUES ('Cnt', 'cnt', ?, 10.00)", devId);

        assertThatThrownBy(() -> jdbc.update("UPDATE games SET review_count = 1, positive_count = 2 WHERE slug = 'cnt'"))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("UPDATE games SET review_count = 2, positive_count = 2 WHERE slug = 'cnt'");
    }

    @Test
    void preventsDuplicateGameInLibrary() {
        long userId = insertUser("player");
        jdbc.update("INSERT INTO games (title, slug, developer_id, price) VALUES ('G', 'g', ?, 5.00)", userId);
        long gameId = jdbc.queryForObject("SELECT id FROM games WHERE slug = 'g'", Long.class);

        jdbc.update("INSERT INTO library_entries (user_id, game_id) VALUES (?, ?)", userId, gameId);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO library_entries (user_id, game_id) VALUES (?, ?)", userId, gameId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private long insertUser(String username) {
        jdbc.update("INSERT INTO users (username, email, password_hash) VALUES (?, ?, 'x')",
                username, username + "@test.local");
        return jdbc.queryForObject("SELECT id FROM users WHERE username = ?", Long.class, username);
    }
}
