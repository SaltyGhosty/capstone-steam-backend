package com.capstone.steam.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.steam.common.exception.BadRequestException;
import com.capstone.steam.common.exception.ConflictException;
import com.capstone.steam.common.exception.NotFoundException;
import com.capstone.steam.notification.event.VerificationEmailEvent;
import com.capstone.steam.user.RoleName;
import com.capstone.steam.user.RoleRepository;
import com.capstone.steam.user.User;
import com.capstone.steam.user.UserRepository;

/**
 * Enlaces de verificación de email: de un solo uso, caducan a las 24 h (configurable) y en la BD solo
 * queda su SHA-256. El token en claro solo viaja en el evento que lleva el correo.
 *
 * <p>Cada enlace guarda los datos del registro que lo generó ({@link PendingRegistration}) y al
 * verificar se aplican a la cuenta <b>esos</b> datos: quien pulsa su enlace se queda con la contraseña que
 * eligió, aunque otra persona se registrara después con el mismo email. Registrarse con el email de una
 * cuenta sin verificar no toca la cuenta, solo emite otro enlace.
 */
@Service
public class EmailVerificationService {

    private static final int MAX_RESENT = 3;

    private final EmailVerificationTokenRepository tokens;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final ApplicationEventPublisher events;
    private final EmailVerificationProperties properties;
    private final Clock clock;
    private final ResendRateLimiter rateLimiter;
    private final AccountDataCheck dataCheck;

    @Autowired
    public EmailVerificationService(EmailVerificationTokenRepository tokens, UserRepository userRepository,
            RoleRepository roleRepository, ApplicationEventPublisher events, EmailVerificationProperties properties,
            Clock clock, ResendRateLimiter rateLimiter, AccountDataCheck dataCheck) {
        this.tokens = tokens;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.events = events;
        this.properties = properties;
        this.clock = clock;
        this.rateLimiter = rateLimiter;
        this.dataCheck = dataCheck;
    }

    /** Emite un enlace nuevo con los datos del registro y devuelve el token en claro. No anula los anteriores. */
    @Transactional
    public String issue(User user, PendingRegistration data) {
        String raw = TokenHashing.newRawToken();
        tokens.save(new EmailVerificationToken(user.getId(), TokenHashing.sha256Hex(raw),
                clock.instant().plus(properties.ttl()), data));
        return raw;
    }

    /** Límite de correos de verificación por email y hora (registro y reenvío comparten el cupo). */
    public void checkRateLimit(String email) {
        rateLimiter.check(normalize(email));
    }

    /**
     * Canjea el token: aplica a la cuenta los datos del enlace, marca el email como verificado, anula los
     * demás enlaces y devuelve el usuario (con roles). Token inexistente, usado o caducado → 400; usuario del
     * enlace ya cogido por otra cuenta → 409 y el enlace queda sin usar.
     */
    @Transactional
    public User verify(String rawToken) {
        Instant now = clock.instant();
        EmailVerificationToken token = tokens.findByTokenHash(TokenHashing.sha256Hex(rawToken))
                .orElseThrow(EmailVerificationService::invalid);
        if (token.isUsed() || !token.getExpiresAt().isAfter(now)) {
            throw invalid();
        }
        Long userId = token.getUserId();
        userRepository.findByIdForUpdate(userId).orElseThrow(EmailVerificationService::invalid); // bloquea la fila
        requireUsernameFree(token.getUsername(), userId);
        // Atómico: de dos clics simultáneos con el mismo enlace solo uno gana
        if (tokens.markUsedIfUnused(token.getId(), now) == 0) {
            throw invalid();
        }
        tokens.invalidateActive(userId, now); // el resto de enlaces del usuario dejan de valer
        User user = userRepository.findWithRolesById(userId).orElseThrow(EmailVerificationService::invalid);
        apply(user, token.pending());
        user.setEmailVerified(true);
        return user;
    }

    /**
     * Verificación a mano por un admin (soporte y seed): aplica los datos del último enlace, si hay, y marca la
     * cuenta como verificada. Idempotente.
     */
    @Transactional
    public User verifyByAdmin(Long userId) {
        userRepository.findByIdForUpdate(userId).orElseThrow(() -> NotFoundException.of("user", userId));
        EmailVerificationToken latest = tokens.findFirstByUserIdOrderByIdDesc(userId).orElse(null);
        if (latest != null) {
            requireUsernameFree(latest.getUsername(), userId);
        }
        Instant now = clock.instant();
        tokens.invalidateActive(userId, now);
        User user = userRepository.findWithRolesById(userId).orElseThrow(() -> NotFoundException.of("user", userId));
        if (latest != null && !user.isEmailVerified()) {
            apply(user, latest.pending());
        }
        user.setEmailVerified(true);
        return user;
    }

    /**
     * Reenvía el enlace. Responde igual exista o no la cuenta, esté o no verificada (el controlador
     * devuelve siempre 204). Solo una cuenta pendiente recibe enlaces nuevos: uno por cada registro pendiente.
     * El límite por email se aplica antes de mirar la BD, así el 429 tampoco delata nada.
     */
    @Transactional
    public void resend(String email) {
        String key = normalize(email);
        rateLimiter.check(key);
        userRepository.findByEmail(key).filter(user -> !user.isEmailVerified()).ifPresent(user -> {
            for (PendingRegistration data : pendingRegistrations(user.getId())) {
                String token = issue(user, data);
                events.publishEvent(new VerificationEmailEvent(user.getEmail(), data.displayName(), data.locale(),
                        token, data.username()));
            }
        });
    }

    /**
     * Un registro pendiente por cada usuario distinto con un enlace vigente sin usar (el más reciente de cada
     * uno, como mucho {@value #MAX_RESENT}): quien es dueño del email recibe un correo por cuenta pedida y
     * pulsa el de la suya, nunca solo el de otra persona. Si no queda ninguno vigente pero la cuenta sigue sin
     * verificar, se repite el último enlace de todos.
     */
    private java.util.List<PendingRegistration> pendingRegistrations(Long userId) {
        java.util.Map<String, PendingRegistration> byUsername = new java.util.LinkedHashMap<>();
        for (EmailVerificationToken t : tokens.findByUserIdAndUsedAtIsNullAndExpiresAtAfterOrderByIdDesc(userId,
                clock.instant())) {
            byUsername.putIfAbsent(t.getUsername().toLowerCase(Locale.ROOT), t.pending());
        }
        if (byUsername.isEmpty()) {
            return tokens.findFirstByUserIdOrderByIdDesc(userId).map(t -> java.util.List.of(t.pending()))
                    .orElse(java.util.List.of());
        }
        return byUsername.values().stream().limit(MAX_RESENT).toList();
    }

    /**
     * ¿Se puede borrar esta cuenta sin verificar para liberar su usuario? Solo si se creó hace más de lo que
     * dura un enlace (se mide desde la creación, no desde el último enlace: reenviar no retiene el nombre),
     * no tiene más roles que USER y DEVELOPER y no tiene datos.
     */
    @Transactional(readOnly = true)
    public boolean isReclaimable(User user) {
        if (user.isEmailVerified() || user.getCreatedAt() == null
                || user.getCreatedAt().plus(properties.ttl()).isAfter(clock.instant())) {
            return false;
        }
        boolean onlyBasicRoles = user.getRoles().stream()
                .allMatch(r -> r.getName() == RoleName.ROLE_USER || r.getName() == RoleName.ROLE_DEVELOPER);
        return onlyBasicRoles && !dataCheck.hasData(user.getId());
    }

    private void requireUsernameFree(String username, Long userId) {
        userRepository.findIdByUsernameIgnoreCase(username).filter(id -> !id.equals(userId)).ifPresent(id -> {
            throw new ConflictException("auth.usernameTaken");
        });
    }

    private void apply(User user, PendingRegistration data) {
        user.setUsername(data.username());
        user.setPasswordHash(data.passwordHash());
        user.setDisplayName(data.displayName());
        user.setLocale(data.locale());
        user.getRoles().clear();
        user.addRole(roleRepository.findByName(RoleName.ROLE_USER).orElseThrow());
        if (data.developer()) {
            user.addRole(roleRepository.findByName(RoleName.ROLE_DEVELOPER).orElseThrow());
        }
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static BadRequestException invalid() {
        return new BadRequestException("auth.verificationInvalid");
    }
}
