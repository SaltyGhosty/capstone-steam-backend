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
 * Link di verifica dell'email: monouso, scadono dopo 24 h (configurabile) e nel DB resta solo il loro
 * SHA-256. Il token in chiaro viaggia solo nell'evento che porta l'email.
 *
 * <p>Ogni link conserva i dati della registrazione che l'ha generato ({@link PendingRegistration}) e alla
 * verifica si applicano all'account <b>quei</b> dati: chi clicca il proprio link si tiene la password che
 * ha scelto, anche se un'altra persona si registra dopo con la stessa email. Registrarsi con l'email di un
 * account non verificato non tocca l'account, emette solo un altro link.
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

    /** Emette un nuovo link con i dati della registrazione e restituisce il token in chiaro. Non annulla i precedenti. */
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

    /** Limite di email di verifica per indirizzo e ora (registrazione e reinvio condividono la quota). */
    public void checkRateLimit(String email) {
        rateLimiter.check(normalize(email));
    }

    /** L'utente di un link valido (non usato e non scaduto); 400 altrimenti, senza distinguere il motivo. */
    @Transactional(readOnly = true)
    public String preview(String rawToken) {
        return liveToken(tokens.findByTokenHash(TokenHashing.sha256Hex(rawToken)).orElse(null), clock.instant()).getUsername();
    }

    /**
     * Riscatta il token con la password di <b>quella</b> registrazione: applica all'account i dati del link, segna
     * l'email come verificata, annulla gli altri link e restituisce l'utente (con ruoli).
     * <ul>
     *   <li>Link inesistente, usato o scaduto → 400 {@code auth.verificationInvalid}.</li>
     *   <li>Password diversa → 400 {@code auth.verificationPasswordInvalid} senza consumare il link; dopo
     *       {@value #MAX_PASSWORD_FAILURES} errori il link viene annullato. Il contatore si salva anche se si risponde 400
     *       ({@code noRollbackFor}, come il rilevamento del riutilizzo del refresh token).</li>
     *   <li>Username del link già preso da un altro account → 409 e il link resta inutilizzato.</li>
     * </ul>
     */
    @Transactional(noRollbackFor = BadRequestException.class)
    public User verify(String rawToken, String password) {
        Instant now = clock.instant();
        String hash = TokenHashing.sha256Hex(rawToken);
        // Ordine dei lock: prima la riga dell'utente e poi i suoi link (così due verifiche di link
        // diversi dello stesso account si alternano invece di andare in deadlock). Con il link bloccato, di due
        // tentativi simultanei il secondo lo vede già consumato o con 5 errori.
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
        // Atomico: di due click simultanei con lo stesso link vince uno solo
        if (tokens.markUsedIfUnused(token.getId(), now) == 0) {
            throw invalid();
        }
        tokens.invalidateActive(userId, now); // gli altri link dell'utente smettono di valere
        User user = userRepository.findWithRolesById(userId).orElseThrow(EmailVerificationService::invalid);
        apply(user, token.pending());
        user.setEmailVerified(true);
        return user;
    }

    /**
     * Verifica manuale da parte di un admin (supporto e seed): applica l'unica registrazione in sospeso dell'account, se
     * c'è, e lo segna come verificato; con più registrazioni diverse, 409. Idempotente. Un account eliminato, 404.
     */
    @Transactional
    public User verifyByAdmin(Long userId) {
        User locked = userRepository.findByIdForUpdate(userId)
                .filter(user -> !user.isDeleted()).orElseThrow(() -> NotFoundException.of("user", userId));
        boolean alreadyVerified = locked.isEmailVerified();
        Instant now = clock.instant();
        PendingRegistration toApply = null;
        if (!alreadyVerified) {
            // Un account non verificato non ha link davvero «consumati» (un link annullato per errori o scaduto
            // resta comunque una registrazione richiesta): si guardano tutti. Con più registrazioni diverse l'admin non può
            // scegliere al posto del proprietario; con una sola si applica quella.
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
     * Reinvia il link. Risponde allo stesso modo che l'account esista o no, sia verificato o no (il controller
     * restituisce sempre 204). Solo un account in sospeso riceve nuovi link: uno per ogni registrazione in sospeso.
     * Il limite per email si applica prima di guardare il DB, così nemmeno il 429 rivela nulla.
     */
    @Transactional
    public void resend(String email) {
        String key = normalize(email);
        rateLimiter.check(key);
        // si blocca la riga dell'utente prima di leggere i suoi link, nello stesso ordine di verify (utente -> link);
        // la riga si legge già con il lock, quindi lo stato «verificato» è quello attuale
        userRepository.findIdByEmail(key).flatMap(userRepository::findByIdForUpdate)
                .filter(user -> !user.isEmailVerified()).ifPresent(user -> {
            List<EmailVerificationToken> all = tokens.findByUserIdOrderByIdDesc(user.getId());
            for (EmailVerificationToken source : pendingRegistrations(user.getId())) {
                PendingRegistration data = source.pending();
                // il nuovo link eredita il massimo di errori di tutti i link della stessa registrazione: sono 5 tentativi
                // per registrazione, e il reinvio non li ripristina
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
     * Una registrazione in sospeso per ogni utente distinto con un link valido non usato (il più recente di ogni
     * registrazione distinta, al massimo {@value #MAX_RESENT}): il proprietario dell'email riceve un'email per ogni account
     * richiesto e clicca quella del proprio (e solo con la sua password entra), mai solo quella di un'altra persona.
     * Se non ne resta nessuno valido ma l'account è ancora non verificato, si ripete l'ultimo link tra tutti.
     */
    private List<EmailVerificationToken> pendingRegistrations(Long userId) {
        List<EmailVerificationToken> pending = distinctTokens(
                tokens.findByUserIdAndUsedAtIsNullAndExpiresAtAfterOrderByIdDesc(userId, clock.instant()));
        if (pending.isEmpty()) {
            // nessuno valido: si ripete l'ultimo, a meno che non sia stato annullato per troppe password errate (per
            // quella registrazione la via è registrarsi di nuovo o l'admin)
            return tokens.findFirstByUserIdOrderByIdDesc(userId)
                    .filter(t -> t.getFailedAttempts() < MAX_PASSWORD_FAILURES).map(List::of).orElse(List.of());
        }
        return pending.stream().limit(MAX_RESENT).toList();
    }

    /** Il link più recente di ogni registrazione distinta (utente e password), dal più recente al più vecchio. */
    private static List<EmailVerificationToken> distinctTokens(List<EmailVerificationToken> list) {
        java.util.Map<String, EmailVerificationToken> distinct = new java.util.LinkedHashMap<>();
        for (EmailVerificationToken t : list) {
            distinct.putIfAbsent(t.getUsername().toLowerCase(Locale.ROOT) + "|" + t.getPasswordHash(), t);
        }
        return List.copyOf(distinct.values());
    }

    /** Il link deve esistere, essere non usato, valido e con meno di 5 password errate. */
    private static EmailVerificationToken liveToken(EmailVerificationToken token, Instant now) {
        if (token == null || token.isUsed() || !token.getExpiresAt().isAfter(now)
                || token.getFailedAttempts() >= MAX_PASSWORD_FAILURES) {
            throw invalid();
        }
        return token;
    }

    /**
     * Si può eliminare questo account non verificato per liberare il suo username? Solo se è stato creato da più di quanto
     * dura un link (si misura dalla creazione, non dall'ultimo link: il reinvio non trattiene il nome),
     * non ha ruoli oltre a USER e DEVELOPER e non ha dati. Un account anonimizzato mai.
     */
    @Transactional(readOnly = true)
    public boolean isReclaimable(User user) {
        if (user.isEmailVerified() || user.isDeleted() || user.getCreatedAt() == null
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
