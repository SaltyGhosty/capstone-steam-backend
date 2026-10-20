package com.capstone.steam.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.steam.common.exception.UnauthorizedException;
import com.capstone.steam.security.JwtProperties;
import com.capstone.steam.user.User;
import com.capstone.steam.user.UserRepository;

/**
 * Emite, rota y revoca refresh tokens. El token en claro solo existe en la respuesta al cliente:
 * en la BD queda su SHA-256, y nunca se escribe en logs.
 *
 * <p>Cada refresh revoca el token usado y emite otro de la misma familia. Si llega uno ya revocado
 * se revoca la familia entera. La revocación es un UPDATE condicional atómico: de varios refresh
 * simultáneos con el mismo token solo uno gana; los demás cuentan como reutilización, cierran la
 * familia y reciben 401 (así funciona la detección de reutilización).
 */
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository repository;
    private final UserRepository userRepository;
    private final Duration lifetime;
    private final Clock clock;

    @Autowired
    public RefreshTokenService(RefreshTokenRepository repository, UserRepository userRepository,
            JwtProperties properties) {
        this(repository, userRepository, properties, Clock.systemUTC());
    }

    RefreshTokenService(RefreshTokenRepository repository, UserRepository userRepository, JwtProperties properties,
            Clock clock) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.lifetime = properties.refreshExpiration();
        this.clock = clock;
    }

    /** Resultado de rotar: el usuario (con roles) y el refresh token nuevo en claro. */
    public record Rotation(User user, String refreshToken) {
    }

    /** Abre una sesión nueva (familia nueva) y devuelve el token en claro. */
    @Transactional
    public String issue(User user) {
        String raw = TokenHashing.newRawToken();
        repository.save(new RefreshToken(user.getId(), TokenHashing.sha256Hex(raw), UUID.randomUUID().toString(),
                clock.instant().plus(lifetime)));
        return raw;
    }

    /**
     * Canjea un refresh token por otro nuevo de la misma familia.
     * {@code noRollbackFor}: las revocaciones previas al error tienen que confirmarse.
     *
     * <p>AVISO: la revocación de la familia solo se guarda porque <b>todas</b> las transacciones de la
     * cadena ({@code AuthService.refresh} y este método) llevan {@code noRollbackFor = UnauthorizedException.class}.
     * Si una sola la pierde, la excepción marca la transacción para rollback y la reutilización no se persiste.
     */
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public Rotation rotate(String rawToken) {
        Instant now = clock.instant();
        RefreshToken current = repository.findByTokenHash(TokenHashing.sha256Hex(rawToken)).orElseThrow(RefreshTokenService::invalid);
        if (current.isRevoked()) {
            // Token ya usado: alguien lo reutiliza. Se cierra toda la familia.
            repository.revokeFamily(current.getFamilyId(), now);
            throw invalid();
        }
        if (!current.getExpiresAt().isAfter(now)) {
            throw invalid();
        }
        User user = userRepository.findWithRolesById(current.getUserId()).orElseThrow(RefreshTokenService::invalid);
        if (!user.isEnabled()) {
            repository.revokeAllForUser(user.getId(), now);
            throw invalid();
        }

        // Revocación atómica: si otra petición se nos adelantó, esto es una reutilización.
        if (repository.revokeIfActive(current.getId(), now) == 0) {
            repository.revokeFamily(current.getFamilyId(), now);
            throw invalid();
        }

        String raw = TokenHashing.newRawToken();
        RefreshToken next = repository.save(
                new RefreshToken(user.getId(), TokenHashing.sha256Hex(raw), current.getFamilyId(), now.plus(lifetime)));
        repository.linkReplacement(current.getId(), next.getId());
        return new Rotation(user, raw);
    }

    /** Cierra la sesión del token dado. Idempotente: un token desconocido no es error. */
    @Transactional
    public void revoke(String rawToken) {
        repository.findByTokenHash(TokenHashing.sha256Hex(rawToken))
                .ifPresent(token -> repository.revokeFamily(token.getFamilyId(), clock.instant()));
    }

    @Transactional
    public void revokeAll(Long userId) {
        repository.revokeAllForUser(userId, clock.instant());
    }

    /** Borra las familias cuyos tokens caducaron todos hace más de {@code grace}. Devuelve las filas borradas. */
    @Transactional
    public int purgeExpired(Duration grace) {
        return repository.deleteExpiredFamilies(clock.instant().minus(grace));
    }

    private static UnauthorizedException invalid() {
        return new UnauthorizedException("auth.refreshInvalid");
    }
}
