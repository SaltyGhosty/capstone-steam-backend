package com.capstone.steam.cover;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Caricamento delle copertine su Cloudinary.
 *
 * @param url     {@code cloudinary://<api_key>:<api_secret>@<cloud_name>}; vuoto = caricamento disattivato.
 *                È un segreto: non compare in log né in errori
 * @param folder  cartella di Cloudinary dove si salvano le copertine
 * @param timeout tempo massimo di ogni richiesta a Cloudinary
 */
@ConfigurationProperties("app.cloudinary")
public record CloudinaryProperties(String url, String folder, Duration timeout) {

    /** Senza il segreto nel {@code toString} (compare nelle trace e nel report delle condizioni di Spring). */
    @Override
    public String toString() {
        return "CloudinaryProperties[url=" + (url == null || url.isBlank() ? "<vacía>" : "<oculta>")
                + ", folder=" + folder + ", timeout=" + timeout + "]";
    }
}
