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

    public AuthService(UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager, JwtService jwtService, ApplicationEventPublisher events) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.events = events;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String username = request.username().trim();
        User user = new User(username, request.email(), passwordEncoder.encode(request.password()));

        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new ConflictException("El nombre de usuario ya está en uso");
        }
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new ConflictException("El email ya está registrado");
        }
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
        userRepository.save(user);

        // El correo de bienvenida sale solo si la transacción hace commit (listener AFTER_COMMIT)
        events.publishEvent(new UserRegisteredEvent(user.getId(), user.getEmail(), user.getDisplayName(), user.getLocale()));

        return issueToken(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        var authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.login().trim(), request.password()));
        var principal = (AppUserPrincipal) authentication.getPrincipal();
        User user = userRepository.findWithRolesById(principal.getId())
                .orElseThrow(() -> NotFoundException.of("Usuario", principal.getId()));
        return issueToken(user);
    }

    private AuthResponse issueToken(User user) {
        String token = jwtService.generateToken(AppUserPrincipal.fromUser(user));
        return AuthResponse.bearer(token, jwtService.getExpirationSeconds(), UserResponse.from(user));
    }

    private Role role(RoleName name) {
        return roleRepository.findByName(name)
                .orElseThrow(() -> new IllegalStateException("Rol no sembrado por Flyway: " + name));
    }
}
