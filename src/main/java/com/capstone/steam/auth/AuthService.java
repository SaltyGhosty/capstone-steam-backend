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
     * Con la verificación de email activa crea la cuenta sin sesión y manda el enlace por correo
     * ({@link RegisterResponse}); desactivada, la cuenta nace verificada y devuelve la sesión.
     */
    @Transactional
    public RegistrationResult register(RegisterRequest request) {
        return verification.required() ? registerPendingVerification(request) : registerVerified(request);
    }

    /**
     * Nadie puede quedarse con un email o un usuario que no ha demostrado suyo:
     * <ul>
     *   <li>Una cuenta verificada nunca se toca (409).</li>
     *   <li>Con el email de una cuenta <b>sin verificar</b> no se toca la fila: solo se emite otro enlace con los datos
     *       de esta petición (límite de correos por email). Al verificar se aplican los datos del enlace pulsado.</li>
     *   <li>Un usuario ocupado por otra cuenta sin verificar se libera solo si esa cuenta es vieja y no tiene datos.</li>
     * </ul>
     * Las filas implicadas se leen con bloqueo, siempre en orden de id (así dos registros cruzados no se interbloquean).
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
                continue; // la borró otra petición entre la consulta y el bloqueo
            }
            if (id.equals(emailId)) {
                byEmail = locked;
            }
            if (id.equals(usernameId)) {
                byUsername = locked;
            }
        }

        if (byEmail != null && byEmail.isEmailVerified()) {
            throw new ConflictException("auth.emailTaken");
        }
        if (byUsername != null && byUsername.isEmailVerified()) {
            throw new ConflictException("auth.usernameTaken");
        }
        boolean takesOverUsername = byUsername != null && (byEmail == null || !byUsername.getId().equals(byEmail.getId()));
        if (takesOverUsername && !verificationService.isReclaimable(byUsername)) {
            throw new ConflictException("auth.usernameTaken");
        }
        // El cupo de correos (registro y reenvío lo comparten) solo se gasta cuando el registro se acepta
        verificationService.checkRateLimit(email);
        if (takesOverUsername) {
            userRepository.delete(byUsername); // sus enlaces caen por ON DELETE CASCADE
            userRepository.flush(); // el DELETE tiene que ir antes de reutilizar el nombre
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

    /** Nombre visible, idioma y roles de la petición de registro. */
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
        user.setEmailVerified(true); // sin verificación de email, la cuenta nace verificada
        userRepository.save(user);

        // El correo de bienvenida sale solo si la transacción hace commit (listener AFTER_COMMIT)
        events.publishEvent(new UserRegisteredEvent(user.getId(), user.getEmail(), user.getDisplayName(), user.getLocale(), null, username));

        return issueToken(user);
    }

    @Transactional // guarda el refresh token
    public AuthResponse login(LoginRequest request) {
        User user = authenticate(request.login(), request.password());
        if (user.isPendingDeletion()) {
            throw pendingDeletion(user);
        }
        return issueToken(user);
    }

    /**
     * Desde el 403 del login: con la contraseña correcta, el dueño cancela su baja y entra. Sin baja pendiente
     * responde igual que el login (no revela más). La decisión (propia, de un admin, anonimizada) la toma
     * {@code cancelBySelf} sobre la fila bloqueada, no la lectura de {@code authenticate}.
     */
    @Transactional
    public AuthResponse recoverAccount(String login, String password) {
        User user = authenticate(login, password);
        accountDeletionService.cancelBySelf(user);
        return issueToken(user);
    }

    /** Contraseña y estado de la cuenta: lo que comparten el login y la recuperación. */
    private User authenticate(String login, String password) {
        var authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(login.trim(), password));
        var principal = (AppUserPrincipal) authentication.getPrincipal();
        User user = userRepository.findWithRolesById(principal.getId())
                .orElseThrow(() -> NotFoundException.of("user", principal.getId()));
        // Solo tras acertar la contraseña (y con la cuenta activa, lo comprueba Spring antes): no se revela nada más
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

    /** Canjea el enlace del correo con la contraseña de ese registro: verifica el email e inicia la sesión. */
    // una cuenta desactivada queda verificada igualmente, y la contraseña errónea tiene que contar (400 sin rollback)
    @Transactional(noRollbackFor = {ForbiddenException.class, BadRequestException.class})
    public AuthResponse verifyEmail(String rawToken, String password) {
        User user = verificationService.verify(rawToken, password);
        if (!user.isEnabled()) {
            throw new ForbiddenException("error.disabled"); // verificada, pero sin sesión
        }
        return issueToken(user);
    }

    /**
     * Rota el refresh token y emite un acceso nuevo con los roles actuales del usuario.
     * AVISO: la revocación por reutilización solo se guarda porque <b>todas</b> las transacciones de la
     * cadena (esta y {@code RefreshTokenService.rotate}) llevan {@code noRollbackFor = UnauthorizedException.class}.
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
