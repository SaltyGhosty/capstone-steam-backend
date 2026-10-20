package com.capstone.steam.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void duplicateUsernameIgnoringCaseIsAnUsernameTaken409() {
        // PostgreSQL y H2 nombran el índice en el mensaje (en H2 en mayúsculas)
        for (String message : new String[] {
                "ERROR: duplicate key value violates unique constraint \"uk_users_username_lower\"",
                "Unique index or primary key violation: \"PUBLIC.UK_USERS_USERNAME_LOWER_INDEX_4 ON PUBLIC.USERS(USERNAME_LOWER NULLS FIRST)\""}) {
            ProblemDetail pd = handler.handleDataIntegrity(
                    new DataIntegrityViolationException("x", new IllegalStateException(message)));

            assertThat(pd.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
            assertThat(pd.getDetail()).isEqualTo("El nombre de usuario ya está en uso");
            assertThat(pd.getProperties()).containsEntry("code", "auth.usernameTaken");
        }
    }

    @Test
    void otherIntegrityViolationsKeepTheGenericConflict() {
        ProblemDetail pd = handler.handleDataIntegrity(
                new DataIntegrityViolationException("x", new IllegalStateException("violates uk_users_email")));

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(pd.getProperties() == null || !pd.getProperties().containsKey("code")).isTrue();
    }
}
