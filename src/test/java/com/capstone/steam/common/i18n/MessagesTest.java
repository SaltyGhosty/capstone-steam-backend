package com.capstone.steam.common.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;

/** Fuera de una petición (tareas programadas, tests unitarios) el texto sale en español, no en el idioma del sistema. */
class MessagesTest {

    private final Locale systemDefault = Locale.getDefault();

    @AfterEach
    void restore() {
        Locale.setDefault(systemDefault);
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void withoutARequestTheTextIsSpanishEvenOnAnEnglishSystem() {
        Locale.setDefault(Locale.US); // como el runner Linux del CI
        LocaleContextHolder.resetLocaleContext();

        assertThat(Messages.currentLocale()).isEqualTo(Messages.DEFAULT);
        assertThat(Messages.get("auth.usernameTaken")).isEqualTo("El nombre de usuario ya está en uso");
    }

    @Test
    void theRequestLanguageWins() {
        LocaleContextHolder.setLocale(Locale.ITALIAN);

        assertThat(Messages.currentLocale()).isEqualTo(Locale.ITALIAN);
        assertThat(Messages.get("auth.usernameTaken")).isNotEqualTo("El nombre de usuario ya está en uso");
    }
}
