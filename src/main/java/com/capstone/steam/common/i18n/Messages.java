package com.capstone.steam.common.i18n;

import java.util.Locale;

import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContext;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;

/**
 * Testi dell'API (errori, motivi di pagamento) in es, en e it, letti da {@code messages*.properties}.
 *
 * <p>{@code messages.properties} è lo spagnolo e fa da fallback per qualsiasi altra lingua.
 * La lingua di ogni richiesta la fissa Spring MVC a partire dall'header {@code Accept-Language}
 * ({@link LocaleContextHolder}); senza header, spagnolo.
 *
 * <p>I testi passano sempre per {@code MessageFormat}: i segnaposto sono {@code {0}}, {@code {1}}…
 * e nei file si usa l'apostrofo tipografico (’) per non dover fare l'escape di {@code '}.
 */
public final class Messages {

    public static final Locale DEFAULT = Locale.forLanguageTag("es");

    private static final ResourceBundleMessageSource SOURCE = new ResourceBundleMessageSource();

    static {
        SOURCE.setBasename("messages");
        SOURCE.setDefaultEncoding("UTF-8");
        SOURCE.setFallbackToSystemLocale(false);
        SOURCE.setAlwaysUseMessageFormat(true);
    }

    private Messages() {
    }

    /** Testo nella lingua della richiesta corrente. */
    public static String get(String code, Object... args) {
        return get(currentLocale(), code, args);
    }

    /**
     * Lingua della richiesta corrente. Fuori da una richiesta (task programmati, unit test) è lo spagnolo:
     * {@link LocaleContextHolder#getLocale()} darebbe quella di sistema, che nel runner della CI è l'inglese.
     */
    public static Locale currentLocale() {
        LocaleContext context = LocaleContextHolder.getLocaleContext();
        Locale locale = context == null ? null : context.getLocale();
        return locale == null ? DEFAULT : locale;
    }

    public static String get(Locale locale, String code, Object... args) {
        return SOURCE.getMessage(code, args, locale);
    }

    /** Come {@link #get(Locale, String, Object...)}, ma se {@code code} non è una chiave restituisce il testo così com'è. */
    public static String getOrSelf(Locale locale, String code) {
        if (code == null) {
            return null;
        }
        try {
            return SOURCE.getMessage(code, null, locale);
        } catch (NoSuchMessageException e) {
            return code; // dati vecchi salvati già come testo
        }
    }

    /** Se la chiave esiste restituisce il suo testo; altrimenti {@code fallback}. */
    public static String getOrDefault(Locale locale, String code, String fallback) {
        return SOURCE.getMessage(code, null, fallback, locale);
    }

    /** "es", "en" o "it" dalle preferenze di un utente → Locale. */
    public static Locale locale(String tag) {
        return tag == null || tag.isBlank() ? DEFAULT : Locale.forLanguageTag(tag);
    }
}
