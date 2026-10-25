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
 * Emette, ruota e revoca refresh token. Il token in chiaro esiste solo nella risposta al client:
 * nel DB resta il suo SHA-256, e non viene mai scritto nei log.
 *
 * <p>Ogni refresh revoca il token usato ed emette un altro della stessa famiglia. Se ne arriva uno già revocato
 * si revoca l'intera famiglia. La revoca è un UPDATE condizionale atomico: tra più refresh
 * simultanei con lo stesso token vince uno solo; gli altri contano come riutilizzo, chiudono la
 * famiglia e ricevono 401 (così funziona il rilevamento del riutilizzo).
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

    /** Risultato della rotazione: l'utente (con ruoli) e il nuovo refresh token in chiaro. */
    public record Rotation(User user, String refreshToken) {
    }

    /** Apre una nuova sessione (nuova famiglia) e restituisce il token in chiaro. */
    @Transactional
    public String issue(User user) {
        String raw = TokenHashing.newRawToken();
        repository.save(new RefreshToken(user.getId(), TokenHashing.sha256Hex(raw), UUID.randomUUID().toString(),
                clock.instant().plus(lifetime)));
        return raw;
    }

    /**
     * Scambia un refresh token con un altro nuovo della stessa famiglia.
     * {@code noRollbackFor}: le revoche precedenti all'errore devono essere confermate.
     *
     * <p>ATTENZIONE: la revoca della famiglia viene salvata solo perché <b>tutte</b> le transazioni della
     * catena ({@code AuthService.refresh} e questo metodo) hanno {@code noRollbackFor = UnauthorizedException.class}.
     * Se anche una sola lo perde, l'eccezione segna la transazione per il rollback e il riutilizzo non viene persistito.
     */
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public Rotation rotate(String rawToken) {
        Instant now = clock.instant();
        RefreshToken current = repository.findByTokenHash(TokenHashing.sha256Hex(rawToken)).orElseThrow(RefreshTokenService::invalid);
        if (current.isRevoked()) {
            // Token già usato: qualcuno lo riutilizza. Si chiude l'intera famiglia.
            repository.revokeFamily(current.getFamilyId(), now);
            throw invalid();
        }
        if (!current.getExpiresAt().isAfter(now)) {
            throw invalid();
        }
        User user = userRepository.findWithRolesById(current.getUserId()).orElseThrow(RefreshTokenService::invalid);
        // In pausa o eliminato conta come disattivato: un login in corso poteva aver salvato un token che revokeAll non ha visto
        if (!user.isEnabled() || user.isPendingDeletion() || user.isDeleted()) {
            repository.revokeAllForUser(user.getId(), now);
            throw invalid();
        }

        // Revoca atomica: se un'altra richiesta ci ha preceduto, è un riutilizzo.
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

    /** Chiude la sessione del token indicato. Idempotente: un token sconosciuto non è un errore. */
    @Transactional
    public void revoke(String rawToken) {
        repository.findByTokenHash(TokenHashing.sha256Hex(rawToken))
                .ifPresent(token -> repository.revokeFamily(token.getFamilyId(), clock.instant()));
    }

    @Transactional
    public void revokeAll(Long userId) {
        repository.revokeAllForUser(userId, clock.instant());
    }

    /** Cancella le famiglie i cui token sono scaduti tutti da più di {@code grace}. Restituisce le righe cancellate. */
    @Transactional
    public int purgeExpired(Duration grace) {
        return repository.deleteExpiredFamilies(clock.instant().minus(grace));
    }

    private static UnauthorizedException invalid() {
        return new UnauthorizedException("auth.refreshInvalid");
    }
}
