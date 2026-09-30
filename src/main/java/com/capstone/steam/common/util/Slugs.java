package com.capstone.steam.common.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** "Pokémon: Edición Oro!" -> "pokemon-edicion-oro" */
public final class Slugs {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");
    private static final Pattern EDGE_DASHES = Pattern.compile("(^-+|-+$)");

    private Slugs() {
    }

    public static String slugify(String input) {
        String ascii = DIACRITICS.matcher(Normalizer.normalize(input, Normalizer.Form.NFD)).replaceAll("");
        String dashed = NON_ALPHANUMERIC.matcher(ascii.toLowerCase(Locale.ROOT)).replaceAll("-");
        String slug = EDGE_DASHES.matcher(dashed).replaceAll("");
        return slug.isEmpty() ? "item" : slug;
    }
}
