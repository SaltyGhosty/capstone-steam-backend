package com.capstone.steam.auth;

import java.util.Locale;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.steam.auth.dto.AccountType;
import com.capstone.steam.auth.dto.AuthResponse;
import com.capstone.steam.auth.dto.LoginRequest;
import com.capstone.steam.auth.dto.RegisterRequest;
import com.capstone.steam.auth.dto.RegisterResponse;
import com.capstone.steam.auth.dto.RegistrationResult;
import com.capstone.steam.common.exception.AccountPendingDeletionException;
import com.capstone.steam.common.exception.BadRequestException;
import com.capstone.steam.common.exception.ConflictException;
import com.capstone.steam.common.exception.ForbiddenException;
import com.capstone.steam.common.exception.NotFoundException;
import com.capstone.steam.common.exception.UnauthorizedException;
import com.capstone.steam.notification.event.UserRegisteredEvent;
import com.capstone.steam.security.AppUserPrincipal;
import com.capstone.steam.security.JwtService;
import com.capstone.steam.user.AccountDeletionService;
import com.capstone.steam.user.DeletionRequester;
import com.capstone.steam.user.Role;
import com.capstone.steam.user.RoleName;
import com.capstone.steam.user.RoleRepository;
import com.capstone.steam.user.User;
import com.capstone.steam.user.UserRepository;
import com.capstone.steam.user.dto.UserResponse;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final ApplicationEventPublisher events;
    private final RefreshTokenService refreshTokenService;
    private final EmailVerificationService verificationService;
    private final EmailVerificationProperties verification;
    private final AccountDeletionService accountDeletionService;

    public AuthService(UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager, JwtService jwtService, ApplicationEventPublisher events,
            RefreshTokenService refreshTokenService, EmailVerificationService verificationService,
            EmailVerificationProperties verification, AccountDeletionService accountDeletionService) {
        this.accountDeletionService = accountDeletionService;
        this.verificationService = verificationService;
        this.verification = verification;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.events = events;
        this.refreshTokenService = refreshTokenService;
    }

    /**
     * Con la verifica email attiva crea l'account senza sessione e invia il link per email
     * ({@link RegisterResponse}); se è disattivata, l'account nasce verificato e restituisce la sessione.
     */
    @Transactional
    public RegistrationResult register(RegisterRequest request) {
        return verification.required() ? registerPendingVerification(request) : registerVerified(request);
    }

    /**
     * Nessuno può tenersi un'email o uno username che non ha dimostrato di possedere:
     * <ul>
     *   <li>Un account verificato o anonimizzato non si tocca mai (409).</li>
     *   <li>Con l'email di un account <b>non verificato</b> non si tocca la riga: si emette solo un altro link con i dati
     *       di questa richiesta (limite di email per indirizzo). Alla verifica si applicano i dati del link cliccato.</li>
     *   <li>Uno username occupato da un altro account non verificato si libera solo se quell'account è vecchio e senza dati.</li>
     * </ul>
     * Le righe coinvolte si leggono con lock, sempre in ordine di id (così due registrazioni incrociate non vanno in deadlock).
     */
    private RegisterResponse registerPendingVerification(RegisterRequest request) {
        String username = request.username().trim();
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        Long emailId = userRepository.findIdByEmail(email).orElse(null);
        Long usernameId = userRepository.findIdByUsernameIgnoreCase(username).orElse(null);
        User byEmail = null;
        User byUsername = null;
        for (Long id : java.util.stream.Stream.of(emailId, usernameId).filter(java.util.Objects::nonNull).distinct()
                .sorted().toList()) {
            User locked = userRepository.findByIdForUpdate(id).orElse(null);
            if (locked == null) {
                continue; // cancellato da un'altra richiesta tra la query e il lock
            }
            if (id.equals(emailId)) {
                byEmail = locked;
            }
            if (id.equals(usernameId)) {
                byUsername = locked;
            }
        }

        // una riga anonimizzata conta come verificata anche se non lo è: i suoi deleted_<id> sono riservati
        if (byEmail != null && (byEmail.isEmailVerified() || byEmail.isDeleted())) {
            throw new ConflictException("auth.emailTaken");
        }
        if (byUsername != null && (byUsername.isEmailVerified() || byUsername.isDeleted())) {
            throw new ConflictException("auth.usernameTaken");
        }
        boolean takesOverUsername = byUsername != null && (byEmail == null || !byUsername.getId().equals(byEmail.getId()));
        if (takesOverUsername && !verificationService.isReclaimable(byUsername)) {
            throw new ConflictException("auth.usernameTaken");
        }
        // La quota di email (registrazione e reinvio la condividono) si consuma solo quando la registrazione viene accettata
        verificationService.checkRateLimit(email);
        if (takesOverUsername) {
            userRepository.delete(byUsername); // i suoi link cadono con ON DELETE CASCADE
            userRepository.flush(); // il DELETE deve precedere il riutilizzo del nome
        }

        String displayName = request.displayName() != null && !request.displayName().isBlank()
                ? request.displayName().trim() : username;
        PendingRegistration data = new PendingRegistration(username, passwordEncoder.encode(request.password()),
                displayName, request.locale() != null ? request.locale() : "es",
                request.accountType() == AccountType.DEVELOPER);
        User user = byEmail;
        if (user == null) {
            user = new User(username, email, data.passwordHash());
            applyRegistration(user, request);
            user.setEmailVerified(false);
            userRepository.save(user);
        }

        String token = verificationService.issue(user, data);
        events.publishEvent(new UserRegisteredEvent(user.getId(), email, displayName, data.locale(), token, username));
        return new RegisterResponse(user.getId(), email, true);
    }

    /** Nome visibile, lingua e ruoli della richiesta di registrazione. */
    private void applyRegistration(User user, RegisterRequest request) {
        if (request.displayName() != null && !request.displayName().isBlank()) {
            user.setDisplayName(request.displayName().trim());
        }
        if (request.locale() != null) {
            user.setLocale(request.locale());
        }
        user.addRole(role(RoleName.ROLE_USER));
        if (request.accountType() == AccountType.DEVELOPER) {
            user.addRole(role(RoleName.ROLE_DEVELOPER));
        }
    }

    private AuthResponse registerVerified(RegisterRequest request) {
        String username = request.username().trim();
        User user = new User(username, request.email(), passwordEncoder.encode(request.password()));

        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new ConflictException("auth.usernameTaken");
        }
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new ConflictException("auth.emailTaken");
        }
        applyRegistration(user, request);
        user.setEmailVerified(true); // senza verifica email, l'account nasce verificato
        userRepository.save(user);

        // L'email di benvenuto parte solo se la transazione fa commit (listener AFTER_COMMIT)
        events.publishEvent(new UserRegisteredEvent(user.getId(), user.getEmail(), user.getDisplayName(), user.getLocale(), null, username));

        return issueToken(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = authenticate(request.login(), request.password());
        if (user.isPendingDeletion()) {
            throw pendingDeletion(user);
        }
        return issueToken(user);
    }

    /**
     * Dal 403 del login: con la password corretta, il proprietario annulla la cancellazione ed entra. Senza cancellazione
     * in sospeso risponde come il login (non rivela altro). La decisione (propria, di un admin, anonimizzata) la prende
     * {@code cancelBySelf} sulla riga bloccata, non la lettura di {@code authenticate}.
     */
    @Transactional
    public AuthResponse recoverAccount(String login, String password) {
        User user = authenticate(login, password);
        accountDeletionService.cancelBySelf(user);
        return issueToken(user);
    }

    /** Password e stato dell'account: ciò che condividono login e recupero. */
    private User authenticate(String login, String password) {
        var authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(login.trim(), password));
        var principal = (AppUserPrincipal) authentication.getPrincipal();
        User user = userRepository.findWithRolesById(principal.getId())
                .orElseThrow(() -> NotFoundException.of("user", principal.getId()));
        // Solo dopo aver indovinato la password (e con l'account attivo, lo verifica Spring prima): non si rivela altro
        if (!user.isEmailVerified()) {
            throw new ForbiddenException("auth.emailNotVerified");
        }
        return user;
    }

    private static AccountPendingDeletionException pendingDeletion(User user) {
        String code = user.getDeletionRequestedBy() == DeletionRequester.ADMIN
                ? "auth.accountDeletionByAdmin" : "auth.accountPendingDeletion";
        return new AccountPendingDeletionException(code, user.getDeletionScheduledAt());
    }

    /** Riscatta il link dell'email con la password di quella registrazione: verifica l'email e avvia la sessione. */
    // un account disattivato o in pausa resta comunque verificato, e la password errata deve contare
    // (400 senza rollback)
    @Transactional(noRollbackFor = {ForbiddenException.class, BadRequestException.class})
    public AuthResponse verifyEmail(String rawToken, String password) {
        User user = verificationService.verify(rawToken, password);
        if (!user.isEnabled()) {
            throw new ForbiddenException("error.disabled"); // verificato, ma senza sessione
        }
        if (user.isPendingDeletion()) {
            throw pendingDeletion(user); // lo stesso 403 del login: il link non apre la sessione durante la pausa
        }
        return issueToken(user);
    }

    /**
     * Ruota il refresh token ed emette un nuovo access token con i ruoli attuali dell'utente.
     * ATTENZIONE: la revoca per riutilizzo viene salvata solo perché <b>tutte</b> le transazioni della
     * catena (questa e {@code RefreshTokenService.rotate}) hanno {@code noRollbackFor = UnauthorizedException.class}.
     */
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public AuthResponse refresh(String refreshToken) {
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(refreshToken);
        return buildResponse(rotation.user(), rotation.refreshToken());
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    private AuthResponse issueToken(User user) {
        return buildResponse(user, refreshTokenService.issue(user));
    }

    private AuthResponse buildResponse(User user, String refreshToken) {
        String token = jwtService.generateToken(AppUserPrincipal.fromUser(user));
        return AuthResponse.bearer(token, jwtService.getExpirationSeconds(), refreshToken, UserResponse.from(user));
    }

    private Role role(RoleName name) {
        return roleRepository.findByName(name)
                .orElseThrow(() -> new IllegalStateException("Rol no sembrado por Flyway: " + name));
    }
}
