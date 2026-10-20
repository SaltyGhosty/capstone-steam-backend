package com.capstone.steam.common.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.capstone.steam.common.i18n.Messages;

/**
 * Todos los errores de la API salen como ProblemDetail (RFC 9457).
 * Las excepciones estándar de Spring MVC las resuelve ResponseEntityExceptionHandler.
 * Los textos salen en el idioma de la petición (cabecera Accept-Language), ver {@link Messages}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ProblemDetail handleApi(ApiException ex) {
        ProblemDetail pd = problem(ex.getStatus(), ex.getLocalizedMessage());
        pd.setProperty("code", ex.getCode());
        return pd;
    }

    @ExceptionHandler(BadCredentialsException.class)
    ProblemDetail handleBadCredentials(BadCredentialsException ex) {
        return problem(HttpStatus.UNAUTHORIZED, Messages.get("error.badCredentials"));
    }

    @ExceptionHandler(DisabledException.class)
    ProblemDetail handleDisabled(DisabledException ex) {
        return problem(HttpStatus.FORBIDDEN, Messages.get("error.disabled"));
    }

    /** Denegaciones de @PreAuthorize (las de las reglas de URL las gestiona RestAccessDeniedHandler). */
    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, Messages.get("error.forbiddenAction"));
    }

    /** Red de seguridad: una restricción UNIQUE/CHECK de la BD que el service no validó antes. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        String cause = ex.getMostSpecificCause().getMessage();
        log.warn("Violación de integridad: {}", cause);
        // dos registros simultáneos con el mismo usuario (sin mayúsculas) se cuelan por la comprobación y los frena el índice
        if (cause != null && cause.toLowerCase(java.util.Locale.ROOT).contains("uk_users_username_lower")) {
            ProblemDetail pd = problem(HttpStatus.CONFLICT, Messages.get("auth.usernameTaken"));
            pd.setProperty("code", "auth.usernameTaken");
            return pd;
        }
        return problem(HttpStatus.CONFLICT, Messages.get("error.dataConflict"));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail handleOptimisticLock(OptimisticLockingFailureException ex) {
        return problem(HttpStatus.CONFLICT, Messages.get("error.optimisticLock"));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        ProblemDetail body = problem(HttpStatus.BAD_REQUEST, Messages.get("error.invalidFields"));
        body.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(body);
    }

    /** Archivo por encima del límite multipart (o del que comprueba el service): 413 traducido, no la página de Tomcat. */
    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
                .body(problem(HttpStatus.CONTENT_TOO_LARGE, Messages.get("cover.tooLarge")));
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        log.error("Error no controlado", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, Messages.get("error.internal"));
    }

    private static ProblemDetail problem(HttpStatus status, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(status.getReasonPhrase());
        return pd;
    }
}
