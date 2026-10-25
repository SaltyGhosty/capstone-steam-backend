package com.capstone.steam.common.exception;

import java.text.DateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import com.capstone.steam.common.i18n.Messages;

/**
 * 403 del login di un account con cancellazione programmata. Porta l'istante dell'eliminazione: il testo lo mostra
 * come data breve nella lingua della richiesta (fuso Europe/Rome) e l'handler lo aggiunge al ProblemDetail.
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

    /** La data già formattata nella lingua della richiesta (MessageFormat non conosce fusi orari né Instant). */
    @Override
    public String getLocalizedMessage() {
        return Messages.get(getCode(), format(scheduledAt, Messages.currentLocale()));
    }

    private static String format(Instant at, Locale locale) {
        DateFormat dates = DateFormat.getDateInstance(DateFormat.SHORT, locale);
        dates.setTimeZone(TimeZone.getTimeZone(ZONE));
        return dates.format(Date.from(at));
    }
}
