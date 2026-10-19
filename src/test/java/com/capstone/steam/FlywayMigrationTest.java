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
 * Verifica que las migraciones V1-V8 se aplican sobre H2 y que las
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
    void appliesAllMigrationsUpToV8() {
        var applied = flyway.info().applied();
        assertThat(applied).hasSize(8);
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("8");
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
