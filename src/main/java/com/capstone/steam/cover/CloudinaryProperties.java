package com.capstone.steam.cover;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Subida de portadas a Cloudinary.
 *
 * @param url     {@code cloudinary://<api_key>:<api_secret>@<cloud_name>}; vacía = subida desactivada.
 *                Es un secreto: no se muestra en logs ni en errores
 * @param folder  carpeta de Cloudinary donde se guardan las portadas
 * @param timeout tiempo máximo de cada petición a Cloudinary
 */
@ConfigurationProperties("app.cloudinary")
public record CloudinaryProperties(String url, String folder, Duration timeout) {

    /** Sin el secreto en el {@code toString} (aparece en trazas y en el informe de condiciones de Spring). */
    @Override
    public String toString() {
        return "CloudinaryProperties[url=" + (url == null || url.isBlank() ? "<vacía>" : "<oculta>")
                + ", folder=" + folder + ", timeout=" + timeout + "]";
    }
}
