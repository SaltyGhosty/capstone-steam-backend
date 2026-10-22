package com.capstone.steam.common.i18n;

import java.util.Locale;

import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContext;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;

/**
 * Textos de la API (errores, motivos de pago) en es, en e it, leídos de {@code messages*.properties}.
 *
 * <p>{@code messages.properties} es el español y hace de respaldo para cualquier otro idioma.
 * El idioma de cada petición lo fija Spring MVC a partir de la cabecera {@code Accept-Language}
 * ({@link LocaleContextHolder}); sin cabecera, español.
 *
 * <p>Los textos siempre pasan por {@code MessageFormat}: los huecos son {@code {0}}, {@code {1}}…
 * y en los ficheros se usa el apóstrofo tipográfico (’) para no tener que escapar {@code '}.
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

    /** Texto en el idioma de la petición actual. */
    public static String get(String code, Object... args) {
        return get(currentLocale(), code, args);
    }

    /**
     * Idioma de la petición actual. Fuera de una petición (tareas programadas, tests unitarios) es el español:
     * {@link LocaleContextHolder#getLocale()} daría el del sistema, que en el runner del CI es el inglés.
     */
    public static Locale currentLocale() {
        LocaleContext context = LocaleContextHolder.getLocaleContext();
        Locale locale = context == null ? null : context.getLocale();
        return locale == null ? DEFAULT : locale;
    }

    public static String get(Locale locale, String code, Object... args) {
        return SOURCE.getMessage(code, args, locale);
    }

    /** Como {@link #get(Locale, String, Object...)}, pero si {@code code} no es una clave devuelve el texto tal cual. */
    public static String getOrSelf(Locale locale, String code) {
        if (code == null) {
            return null;
        }
        try {
            return SOURCE.getMessage(code, null, locale);
        } catch (NoSuchMessageException e) {
            return code; // datos antiguos guardados ya como texto
        }
    }

    /** Si existe la clave devuelve su texto; si no, {@code fallback}. */
    public static String getOrDefault(Locale locale, String code, String fallback) {
        return SOURCE.getMessage(code, null, fallback, locale);
    }

    /** "es", "en" o "it" de las preferencias de un usuario → Locale. */
    public static Locale locale(String tag) {
        return tag == null || tag.isBlank() ? DEFAULT : Locale.forLanguageTag(tag);
    }
}
