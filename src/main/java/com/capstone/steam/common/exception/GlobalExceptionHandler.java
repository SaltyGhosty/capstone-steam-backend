package com.capstone.steam.common.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.core.PropertyReferenceException;
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
 * Tutti gli errori dell'API escono come ProblemDetail (RFC 9457).
 * Le eccezioni standard di Spring MVC le risolve ResponseEntityExceptionHandler.
 * I testi escono nella lingua della richiesta (header Accept-Language), vedi {@link Messages}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ProblemDetail handleApi(ApiException ex) {
        return withCode(ex);
    }

    /** 403 di un account in pausa: oltre al codice, la data (ISO-8601) in cui verrà eliminato. */
    @ExceptionHandler(AccountPendingDeletionException.class)
    ProblemDetail handlePendingDeletion(AccountPendingDeletionException ex) {
        ProblemDetail pd = withCode(ex);
        pd.setProperty("deletionScheduledAt", ex.getScheduledAt().toString());
        return pd;
    }

    /** 429 di un limite a finestra: con l'header Retry-After (secondi) perché il client sappia quando riprovare. */
    @ExceptionHandler(RateLimitedException.class)
    ResponseEntity<ProblemDetail> handleRateLimited(RateLimitedException ex) {
        ProblemDetail pd = problem(ex.getStatus(), ex.getLocalizedMessage());
        pd.setProperty("code", ex.getCode());
        return ResponseEntity.status(ex.getStatus()).header(HttpHeaders.RETRY_AFTER,
                String.valueOf(ex.getRetryAfterSeconds())).body(pd);
    }

    @ExceptionHandler(BadCredentialsException.class)
    ProblemDetail handleBadCredentials(BadCredentialsException ex) {
        return problem(HttpStatus.UNAUTHORIZED, Messages.get("error.badCredentials"));
    }

    @ExceptionHandler(DisabledException.class)
    ProblemDetail handleDisabled(DisabledException ex) {
        return problem(HttpStatus.FORBIDDEN, Messages.get("error.disabled"));
    }

    /** Rifiuti di @PreAuthorize (quelli delle regole URL li gestisce RestAccessDeniedHandler). */
    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, Messages.get("error.forbiddenAction"));
    }

    /** Rete di sicurezza: un vincolo UNIQUE/CHECK del DB che il service non aveva validato prima. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        String cause = ex.getMostSpecificCause().getMessage();
        log.warn("Violación de integridad: {}", cause);
        // due registrazioni simultanee con lo stesso username (senza maiuscole) superano il controllo e le ferma l'indice
        if (cause != null && cause.toLowerCase(java.util.Locale.ROOT).contains("uk_users_username_lower")) {
            ProblemDetail pd = problem(HttpStatus.CONFLICT, Messages.get("auth.usernameTaken"));
            pd.setProperty("code", "auth.usernameTaken");
            return pd;
        }
        return problem(HttpStatus.CONFLICT, Messages.get("error.dataConflict"));
    }

    /** Un {@code sort} con un campo che l'entità non ha: errore del client, non del server. */
    @ExceptionHandler(PropertyReferenceException.class)
    ProblemDetail handleBadSortProperty(PropertyReferenceException ex) {
        return problem(HttpStatus.BAD_REQUEST, Messages.get("error.invalidFields"));
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

    /** File oltre il limite multipart (o quello che controlla il service): 413 tradotto, non la pagina di Tomcat. */
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

    private static ProblemDetail withCode(ApiException ex) {
        ProblemDetail pd = problem(ex.getStatus(), ex.getLocalizedMessage());
        pd.setProperty("code", ex.getCode());
        return pd;
    }

    private static ProblemDetail problem(HttpStatus status, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(status.getReasonPhrase());
        return pd;
    }
}
