package com.capstone.steam.auth;

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
import com.capstone.steam.common.exception.ConflictException;
import com.capstone.steam.common.exception.NotFoundException;
import com.capstone.steam.common.exception.UnauthorizedException;
import com.capstone.steam.notification.event.UserRegisteredEvent;
import com.capstone.steam.security.AppUserPrincipal;
import com.capstone.steam.security.JwtService;
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

    public AuthService(UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager, JwtService jwtService, ApplicationEventPublisher events,
            RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.events = events;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String username = request.username().trim();
        User user = new User(username, request.email(), passwordEncoder.encode(request.password()));

        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new ConflictException("auth.usernameTaken");
        }
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new ConflictException("auth.emailTaken");
        }
        if (request.displayName() != null && !request.displayName().isBlank()) {
            user.setDisplayName(request.displayName().trim());
        }
        if (request.locale() != null) {
            user.setLocale(request.locale());
        }

        user.setEmailVerified(true); // provisional: la Task 3 lo condiciona al interruptor de verificación
        user.addRole(role(RoleName.ROLE_USER));
        if (request.accountType() == AccountType.DEVELOPER) {
            user.addRole(role(RoleName.ROLE_DEVELOPER));
        }
        userRepository.save(user);

        // El correo de bienvenida sale solo si la transacción hace commit (listener AFTER_COMMIT)
        events.publishEvent(new UserRegisteredEvent(user.getId(), user.getEmail(), user.getDisplayName(), user.getLocale()));

        return issueToken(user);
    }

    @Transactional // guarda el refresh token
    public AuthResponse login(LoginRequest request) {
        var authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.login().trim(), request.password()));
        var principal = (AppUserPrincipal) authentication.getPrincipal();
        User user = userRepository.findWithRolesById(principal.getId())
                .orElseThrow(() -> NotFoundException.of("user", principal.getId()));
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
