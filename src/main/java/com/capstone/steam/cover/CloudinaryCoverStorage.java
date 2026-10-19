package com.capstone.steam.cover;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Sube portadas a Cloudinary con su API REST firmada (sin SDK).
 *
 * <p>La firma es el SHA-1 en hexadecimal de los parámetros firmados ordenados alfabéticamente
 * ({@code a=1&b=2}) seguidos del {@code api_secret}. {@code file} y {@code api_key} no se firman.
 * El secreto solo se usa para firmar: no sale en logs, respuestas ni errores.
 */
@Component
public class CloudinaryCoverStorage implements CoverStorage {

    private static final Logger log = LoggerFactory.getLogger(CloudinaryCoverStorage.class);
    private static final Pattern CLOUDINARY_URL = Pattern.compile("^cloudinary://([^:@/]+):([^@/]+)@([^/@]+)$");
    private static final String API = "https://api.cloudinary.com/v1_1/";

    private final RestClient http;
    private final JsonMapper json = JsonMapper.builder().build();
    private final Clock clock;
    private final String folder;
    private final String apiKey;
    private final String apiSecret;
    private final String cloudName;

    @Autowired
    public CloudinaryCoverStorage(CloudinaryProperties properties, RestClient cloudinaryRestClient) {
        this(properties, cloudinaryRestClient, Clock.systemUTC());
    }

    CloudinaryCoverStorage(CloudinaryProperties properties, RestClient http, Clock clock) {
        this.http = http;
        this.clock = clock;
        this.folder = properties.folder();
        Matcher m = properties.url() == null ? null : CLOUDINARY_URL.matcher(properties.url().trim());
        if (m != null && m.matches()) {
            this.apiKey = m.group(1);
            this.apiSecret = m.group(2);
            this.cloudName = m.group(3);
        } else {
            this.apiKey = null;
            this.apiSecret = null;
            this.cloudName = null;
            if (properties.url() != null && !properties.url().isBlank()) {
                // sin el valor: es un secreto
                log.warn("CLOUDINARY_URL no tiene el formato cloudinary://<api_key>:<api_secret>@<cloud_name>; subida de portadas desactivada");
            }
        }
    }

    @Override
    public boolean enabled() {
        return cloudName != null;
    }

    String cloudName() {
        return cloudName;
    }

    String apiKey() {
        return apiKey;
    }

    @Override
    public String upload(long gameId, byte[] bytes, ImageType type) {
        if (!enabled()) {
            throw new CoverUploadException(null);
        }
        Map<String, String> signed = new TreeMap<>();
        signed.put("folder", folder);
        signed.put("overwrite", "true");
        signed.put("public_id", "game-" + gameId);
        signed.put("timestamp", String.valueOf(clock.instant().getEpochSecond()));

        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.parseMediaType(type.mediaType()));
        form.add("file", new HttpEntity<>(new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return "game-" + gameId;
            }
        }, fileHeaders));
        signed.forEach(form::add);
        form.add("api_key", apiKey);
        form.add("signature", signature(signed, apiSecret));

        try {
            String body = http.post()
                    .uri(API + cloudName + "/image/upload")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .body(String.class);
            String url = body == null ? "" : json.readTree(body).path("secure_url").asString("");
            if (!url.startsWith("https://")) {
                throw new CoverUploadException(null);
            }
            return url;
        } catch (RestClientException | JacksonException | IllegalArgumentException ex) {
            // solo el tipo del error: ni la URL de configuración ni el cuerpo de la petición
            log.warn("Cloudinary no ha aceptado la portada del juego {}: {}", gameId, ex.getClass().getSimpleName());
            throw new CoverUploadException(ex);
        }
    }

    /** SHA-1 hex de {@code k1=v1&k2=v2...} (claves en orden alfabético) + el secreto. */
    static String signature(Map<String, String> params, String secret) {
        StringBuilder toSign = new StringBuilder();
        new TreeMap<>(params).forEach((k, v) -> {
            if (!toSign.isEmpty()) {
                toSign.append('&');
            }
            toSign.append(k).append('=').append(v);
        });
        toSign.append(secret);
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-1").digest(toSign.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 no disponible", e);
        }
    }
}
