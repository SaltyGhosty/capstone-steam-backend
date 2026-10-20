package com.capstone.steam.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    static final int MAX_PASSWORD_FAILURES = 5;

    private final EmailVerificationTokenRepository tokens;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final ApplicationEventPublisher events;
    private final EmailVerificationProperties properties;
    private final Clock clock;
    private final ResendRateLimiter rateLimiter;
    private final AccountDataCheck dataCheck;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public EmailVerificationService(EmailVerificationTokenRepository tokens, UserRepository userRepository,
            RoleRepository roleRepository, ApplicationEventPublisher events, EmailVerificationProperties properties,
            Clock clock, ResendRateLimiter rateLimiter, AccountDataCheck dataCheck,
            PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
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
        return issue(user, data, 0);
    }

    private String issue(User user, PendingRegistration data, int failedAttempts) {
        String raw = TokenHashing.newRawToken();
        tokens.save(new EmailVerificationToken(user.getId(), TokenHashing.sha256Hex(raw),
                clock.instant().plus(properties.ttl()), data, failedAttempts));
        return raw;
    }

    /** Límite de correos de verificación por email y hora (registro y reenvío comparten el cupo). */
    public void checkRateLimit(String email) {
        rateLimiter.check(normalize(email));
    }

    /** El usuario de un enlace vigente (sin usar y sin caducar); 400 si no, sin distinguir por qué. */
    @Transactional(readOnly = true)
    public String preview(String rawToken) {
        return liveToken(tokens.findByTokenHash(TokenHashing.sha256Hex(rawToken)).orElse(null), clock.instant()).getUsername();
    }

    /**
     * Canjea el token con la contraseña de <b>ese</b> registro: aplica a la cuenta los datos del enlace, marca el
     * email como verificado, anula los demás enlaces y devuelve el usuario (con roles).
     * <ul>
     *   <li>Enlace inexistente, usado o caducado → 400 {@code auth.verificationInvalid}.</li>
     *   <li>Contraseña distinta → 400 {@code auth.verificationPasswordInvalid} sin gastar el enlace; a los
     *       {@value #MAX_PASSWORD_FAILURES} fallos el enlace se anula. El contador se guarda aunque se responda 400
     *       ({@code noRollbackFor}, igual que la detección de reutilización del refresh token).</li>
     *   <li>Usuario del enlace ya cogido por otra cuenta → 409 y el enlace queda sin usar.</li>
     * </ul>
     */
    @Transactional(noRollbackFor = BadRequestException.class)
    public User verify(String rawToken, String password) {
        Instant now = clock.instant();
        String hash = TokenHashing.sha256Hex(rawToken);
        // Orden de bloqueos: primero la fila del usuario y luego sus enlaces (así dos verificaciones de enlaces
        // distintos de la misma cuenta se turnan en vez de interbloquearse). Con el enlace bloqueado, de dos
        // intentos simultáneos el segundo lo ve ya gastado o con 5 fallos.
        Long userId = tokens.findUserIdByTokenHash(hash).orElseThrow(EmailVerificationService::invalid);
        userRepository.findByIdForUpdate(userId).orElseThrow(EmailVerificationService::invalid);
        EmailVerificationToken token = liveToken(tokens.findByTokenHashForUpdate(hash).orElse(null), now);
        if (password == null || !passwordEncoder.matches(password, token.getPasswordHash())) {
            tokens.incrementFailedAttempts(token.getUserId(), token.getUsername(), token.getPasswordHash());
            tokens.invalidateIfTooManyFailures(token.getUserId(), token.getUsername(), token.getPasswordHash(), now,
                    MAX_PASSWORD_FAILURES);
            throw new BadRequestException("auth.verificationPasswordInvalid");
        }
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
     * Verificación a mano por un admin (soporte y seed): aplica el único registro pendiente que tenga la cuenta, si
     * hay, y la marca como verificada; con varios registros distintos, 409. Idempotente.
     */
    @Transactional
    public User verifyByAdmin(Long userId) {
        boolean alreadyVerified = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> NotFoundException.of("user", userId)).isEmailVerified();
        Instant now = clock.instant();
        PendingRegistration toApply = null;
        if (!alreadyVerified) {
            // Una cuenta sin verificar no tiene enlaces «gastados» de verdad (un enlace anulado por fallos o caducado
            // sigue siendo un registro pedido): se miran todos. Con varios registros distintos el admin no puede
            // elegir por el dueño; con uno solo se aplica ese mismo.
            List<EmailVerificationToken> registrations = distinctTokens(tokens.findByUserIdOrderByIdDesc(userId));
            if (registrations.size() > 1) {
                throw new ConflictException("auth.verificationAmbiguous");
            }
            if (!registrations.isEmpty()) {
                toApply = registrations.get(0).pending();
                requireUsernameFree(toApply.username(), userId);
            }
        }
        tokens.invalidateActive(userId, now);
        User user = userRepository.findWithRolesById(userId).orElseThrow(() -> NotFoundException.of("user", userId));
        if (toApply != null) {
            apply(user, toApply);
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
            List<EmailVerificationToken> all = tokens.findByUserIdOrderByIdDesc(user.getId());
            for (EmailVerificationToken source : pendingRegistrations(user.getId())) {
                PendingRegistration data = source.pending();
                // el enlace nuevo hereda el máximo de fallos de todos los enlaces del mismo registro: son 5 intentos
                // por registro, y reenviar no los devuelve
                int inherited = all.stream()
                        .filter(t -> t.getUsername().equalsIgnoreCase(data.username())
                                && t.getPasswordHash().equals(data.passwordHash()))
                        .mapToInt(EmailVerificationToken::getFailedAttempts).max().orElse(0);
                String token = issue(user, data, inherited);
                events.publishEvent(new VerificationEmailEvent(user.getEmail(), data.displayName(), data.locale(),
                        token, data.username()));
            }
        });
    }

    /**
     * Un registro pendiente por cada usuario distinto con un enlace vigente sin usar (el más reciente de cada
     * registro distinto, como mucho {@value #MAX_RESENT}): el dueño del email recibe un correo por cada cuenta
     * pedida y pulsa el de la suya (y solo con su contraseña entra), nunca solo el de otra persona. Si no queda ninguno vigente pero la cuenta sigue sin
     * verificar, se repite el último enlace de todos.
     */
    private List<EmailVerificationToken> pendingRegistrations(Long userId) {
        List<EmailVerificationToken> pending = distinctTokens(
                tokens.findByUserIdAndUsedAtIsNullAndExpiresAtAfterOrderByIdDesc(userId, clock.instant()));
        if (pending.isEmpty()) {
            // ninguno vigente: se repite el último, salvo que se anulara por demasiadas contraseñas erróneas (para
            // ese registro la vía es registrarse de nuevo o el admin)
            return tokens.findFirstByUserIdOrderByIdDesc(userId)
                    .filter(t -> t.getFailedAttempts() < MAX_PASSWORD_FAILURES).map(List::of).orElse(List.of());
        }
        return pending.stream().limit(MAX_RESENT).toList();
    }

    /** El enlace más reciente de cada registro distinto (usuario y contraseña), del más reciente al más antiguo. */
    private static List<EmailVerificationToken> distinctTokens(List<EmailVerificationToken> list) {
        java.util.Map<String, EmailVerificationToken> distinct = new java.util.LinkedHashMap<>();
        for (EmailVerificationToken t : list) {
            distinct.putIfAbsent(t.getUsername().toLowerCase(Locale.ROOT) + "|" + t.getPasswordHash(), t);
        }
        return List.copyOf(distinct.values());
    }

    /** El enlace tiene que existir, estar sin usar, vigente y con menos de 5 contraseñas erróneas. */
    private static EmailVerificationToken liveToken(EmailVerificationToken token, Instant now) {
        if (token == null || token.isUsed() || !token.getExpiresAt().isAfter(now)
                || token.getFailedAttempts() >= MAX_PASSWORD_FAILURES) {
            throw invalid();
        }
        return token;
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
