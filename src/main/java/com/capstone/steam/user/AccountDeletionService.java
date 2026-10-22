package com.capstone.steam.user;

import java.time.Clock;
import java.time.Instant;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import org.hibernate.Hibernate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.steam.auth.RefreshTokenService;
import com.capstone.steam.common.exception.AccountPendingDeletionException;
import com.capstone.steam.common.exception.BadRequestException;
import com.capstone.steam.common.exception.NotFoundException;
import com.capstone.steam.notification.event.AccountDeletionCancelledEvent;
import com.capstone.steam.notification.event.AccountDeletionScheduledEvent;
import com.capstone.steam.security.AccessTokenRevocations;
import com.capstone.steam.user.dto.UserResponse;

/** Baja de cuentas: pedirla (el dueño o un admin) y cancelarla antes de que venza el plazo. */
@Service
public class AccountDeletionService {

    private final UserRepository userRepository;
    private final AdminGuard adminGuard;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final AccessTokenRevocations accessTokenRevocations;
    private final ApplicationEventPublisher events;
    private final AccountDeletionProperties properties;
    private final Clock clock;
    private final EntityManager entityManager;

    public AccountDeletionService(UserRepository userRepository, AdminGuard adminGuard,
            PasswordEncoder passwordEncoder, RefreshTokenService refreshTokenService,
            AccessTokenRevocations accessTokenRevocations, ApplicationEventPublisher events,
            AccountDeletionProperties properties, Clock clock, EntityManager entityManager) {
        this.userRepository = userRepository;
        this.adminGuard = adminGuard;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.accessTokenRevocations = accessTokenRevocations;
        this.events = events;
        this.properties = properties;
        this.clock = clock;
        this.entityManager = entityManager;
    }

    /**
     * El dueño pide su baja con su contraseña. Devuelve cuándo se eliminará la cuenta. La contraseña se comprueba
     * antes de bloquear: bcrypt no retiene a los admins bloqueados, y un error de contraseña da 400 aunque sea el
     * último admin. El estado se vuelve a mirar sobre la fila bloqueada.
     */
    @Transactional
    public Instant requestOwn(Long userId, String password) {
        User user = userRepository.findWithRolesById(userId).orElseThrow(() -> NotFoundException.of("user", userId));
        if (password == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BadRequestException("user.deletionWrongPassword");
        }
        User locked = lockAndGuard(user);
        if (!locked.isPendingDeletion()) { // si ya estaba pendiente, se conserva la fecha original
            schedule(locked, DeletionRequester.SELF);
        }
        return locked.getDeletionScheduledAt();
    }

    /** Un admin programa la baja de otra cuenta. Idempotente si ya estaba pendiente. */
    @Transactional
    public UserResponse scheduleByAdmin(Long adminId, Long userId) {
        if (adminId.equals(userId)) {
            throw new BadRequestException("user.cannotDeleteSelf");
        }
        User user = userRepository.findWithRolesById(userId).orElseThrow(() -> NotFoundException.of("user", userId));
        User locked = lockAndGuard(user);
        if (!locked.isPendingDeletion()) {
            schedule(locked, DeletionRequester.ADMIN);
        }
        return UserResponse.from(locked);
    }

    /** Un admin cancela cualquier baja pendiente. Idempotente. */
    @Transactional
    public UserResponse cancelByAdmin(Long userId) {
        User user = lockFresh(userId);
        if (user.isDeleted()) {
            throw NotFoundException.of("user", userId);
        }
        if (user.isPendingDeletion()) {
            cancel(user);
        }
        return UserResponse.from(user);
    }

    /**
     * El dueño recupera su cuenta (lo usa la recuperación desde el login). Bloquea y relee la fila por su cuenta:
     * da igual que {@code user} se cargara antes, su estado en memoria no se usa. Sobre la fila bloqueada:
     * anonimizada, falla como una contraseña incorrecta (401); con la baja pedida por un admin, 403 y no se
     * cancela nada; con la baja propia, se cancela; sin baja, no hace nada.
     */
    @Transactional
    public void cancelBySelf(User user) {
        User locked = lockFresh(user.getId());
        if (locked.isDeleted()) {
            throw new BadCredentialsException("cuenta eliminada");
        }
        if (!locked.isPendingDeletion()) {
            return;
        }
        if (locked.getDeletionRequestedBy() == DeletionRequester.ADMIN) {
            throw new AccountPendingDeletionException("auth.accountDeletionByAdmin", locked.getDeletionScheduledAt());
        }
        cancel(locked);
    }

    /**
     * Orden de bloqueos: si es admin, el guard primero (bloquea a los admins por id); después la fila del
     * objetivo, y sobre ella se vuelve a comprobar el estado.
     */
    private User lockAndGuard(User user) {
        if (user.hasRole(RoleName.ROLE_ADMIN)) {
            adminGuard.requireAnotherAdmin(user.getId());
        }
        User locked = lockFresh(user.getId());
        if (locked.isDeleted()) {
            throw NotFoundException.of("user", user.getId());
        }
        return locked;
    }

    /**
     * Bloquea la fila y la relee de la BD. {@code findByIdForUpdate} dentro de la misma sesión devolvería la
     * entidad ya cargada sin refrescarla, y el estado (pendiente, eliminada) sería el de antes del bloqueo.
     */
    private User lockFresh(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> NotFoundException.of("user", userId));
        entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE);
        Hibernate.initialize(user.getRoles()); // el refresh deja los roles sin cargar y la respuesta los lee
        return user;
    }

    private void schedule(User user, DeletionRequester by) {
        Instant at = clock.instant().plus(properties.deletionGrace());
        user.scheduleDeletion(at, by);
        refreshTokenService.revokeAll(user.getId());
        accessTokenRevocations.revokeUser(user.getId());
        events.publishEvent(new AccountDeletionScheduledEvent(user.getEmail(), user.getDisplayName(),
                user.getLocale(), at, by));
    }

    private void cancel(User user) {
        user.cancelDeletion();
        events.publishEvent(new AccountDeletionCancelledEvent(user.getEmail(), user.getDisplayName(),
                user.getLocale()));
    }
}
