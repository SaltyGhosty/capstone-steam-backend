package com.capstone.steam.common.exception;

import java.text.DateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import org.springframework.context.i18n.LocaleContextHolder;

import com.capstone.steam.common.i18n.Messages;

/**
 * 403 del login de una cuenta con la baja programada. Lleva el instante de la eliminación: el texto lo muestra
 * como fecha corta en el idioma de la petición (zona Europe/Rome) y el handler lo añade al ProblemDetail.
 */
public class AccountPendingDeletionException extends ForbiddenException {

    private static final ZoneId ZONE = ZoneId.of("Europe/Rome");

    private final Instant scheduledAt;

    /** @param code {@code auth.accountPendingDeletion} o {@code auth.accountDeletionByAdmin} */
    public AccountPendingDeletionException(String code, Instant scheduledAt) {
        super(code, format(scheduledAt, Messages.DEFAULT));
        this.scheduledAt = scheduledAt;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    /** La fecha ya formateada en el idioma de la petición (MessageFormat no sabe de zonas ni de Instant). */
    @Override
    public String getLocalizedMessage() {
        return Messages.get(getCode(), format(scheduledAt, LocaleContextHolder.getLocale()));
    }

    private static String format(Instant at, Locale locale) {
        DateFormat dates = DateFormat.getDateInstance(DateFormat.SHORT, locale);
        dates.setTimeZone(TimeZone.getTimeZone(ZONE));
        return dates.format(Date.from(at));
    }
}
