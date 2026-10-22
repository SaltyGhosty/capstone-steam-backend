package com.capstone.steam.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.capstone.steam.auth.AuthService;
import com.capstone.steam.auth.dto.AccountType;
import com.capstone.steam.auth.dto.RegisterRequest;
import com.capstone.steam.common.exception.ConflictException;

/**
 * Los nombres {@code deleted_<id>} de una cuenta anonimizada quedan reservados aunque la cuenta nunca se
 * verificara: la limpieza de cuentas sin verificar (D54) no puede borrar ni reutilizar la fila. Con la verificación
 * de email activa y commits reales (la anonimización abre su propia transacción), así que usa su propio H2.
 * El registro se llama al servicio para saltarse la validación del DTO, que ya rechaza esos nombres.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:anonymized_identifiers;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "app.auth.email-verification.required=true",
        "spring.mail.username=" // el .env local puede traer un Gmail: este test no envía correos
})
class AnonymizedIdentifiersIntegrationTest {

    @Autowired private AuthService authService;
    @Autowired private AccountAnonymizer anonymizer;
    @Autowired private JdbcTemplate jdbc;

    private static RegisterRequest request(String username, String email) {
        return new RegisterRequest(username, email, "password123", null, AccountType.PLAYER, null);
    }

    @Test
    void anUnverifiedAnonymizedAccountKeepsItsReservedNames() {
        authService.register(request("fantasma", "fantasma@test.com")); // sin verificar
        long id = jdbc.queryForObject("SELECT id FROM users WHERE username = 'fantasma'", Long.class);
        // el admin programó su baja hace 14 días y la cuenta es más vieja que un enlace (sería «recuperable»)
        Instant now = Instant.now();
        jdbc.update("UPDATE users SET deletion_scheduled_at = ?, deletion_requested_by = 'ADMIN', created_at = ? WHERE id = ?",
                Timestamp.from(now.minusSeconds(60)), Timestamp.from(now.minus(Duration.ofDays(15))), id);

        anonymizer.anonymize(id);

        assertThat(jdbc.queryForObject("SELECT email_verified FROM users WHERE id = ?", Boolean.class, id)).isTrue();

        // aunque la fila se anonimizara sin marcarla verificada (antes de este arreglo), D54 no la toca
        jdbc.update("UPDATE users SET email_verified = FALSE WHERE id = ?", id);
        assertThatThrownBy(() -> authService.register(request("deleted_" + id, "nuevo@test.com")))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> authService.register(request("nuevo", "deleted_" + id + "@deleted.invalid")))
                .isInstanceOf(ConflictException.class);

        assertThat(jdbc.queryForObject("SELECT username FROM users WHERE id = ?", String.class, id))
                .isEqualTo("deleted_" + id);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM email_verification_tokens WHERE user_id = ?",
                Integer.class, id)).isZero();
    }
}
