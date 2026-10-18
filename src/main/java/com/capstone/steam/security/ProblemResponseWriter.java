package com.capstone.steam.security;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.capstone.steam.common.i18n.Messages;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * Escribe un ProblemDetail (RFC 9457) directamente en la respuesta. Lo usan los handlers
 * de seguridad, que actúan en el filtro antes de llegar a Spring MVC (@RestControllerAdvice).
 * Como aquí Spring MVC aún no ha fijado el idioma, se lee directamente de Accept-Language.
 */
@Component
public class ProblemResponseWriter {

    private final JsonMapper jsonMapper;

    public ProblemResponseWriter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    /** @param code clave del texto en messages*.properties */
    public void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String code)
            throws IOException {
        Locale locale = request.getHeader(HttpHeaders.ACCEPT_LANGUAGE) == null ? Messages.DEFAULT : request.getLocale();
        String detail = Messages.get(locale, code);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", status.getReasonPhrase());
        body.put("status", status.value());
        body.put("detail", detail);
        body.put("instance", request.getRequestURI());

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        jsonMapper.writeValue(response.getOutputStream(), body);
    }
}
