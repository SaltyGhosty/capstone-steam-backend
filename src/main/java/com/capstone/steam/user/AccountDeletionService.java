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

/** Cancellazione degli account: richiederla (il proprietario o un admin) e annullarla prima che scada il periodo. */
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
     * Il proprietario chiede la cancellazione con la sua password. Restituisce quando l'account verrà eliminato. La password si verifica
     * prima del lock: bcrypt non trattiene gli admin bloccati, e una password errata dà 400 anche se è
     * l'ultimo admin. Lo stato si ricontrolla sulla riga bloccata.
     */
    @Transactional
    public Instant requestOwn(Long userId, String password) {
        User user = userRepository.findWithRolesById(userId).orElseThrow(() -> NotFoundException.of("user", userId));
        if (password == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BadRequestException("user.deletionWrongPassword");
        }
        User locked = lockAndGuard(user);
        if (!locked.isPendingDeletion()) { // se era già in sospeso, si conserva la data originale
            schedule(locked, DeletionRequester.SELF);
        }
        return locked.getDeletionScheduledAt();
    }

    /** Un admin programma la cancellazione di un altro account. Idempotente se era già in sospeso. */
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

    /** Un admin annulla qualsiasi cancellazione in sospeso. Idempotente. */
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
     * Il proprietario recupera il suo account (lo usa il recupero dal login). Blocca e rilegge la riga per conto suo:
     * non importa che {@code user} sia stato caricato prima, il suo stato in memoria non si usa. Sulla riga bloccata:
     * anonimizzato, fallisce come una password errata (401); con la cancellazione chiesta da un admin, 403 e non si
     * annulla nulla; con la cancellazione propria, si annulla; senza cancellazione, non fa nulla.
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
     * Ordine dei lock: se è admin, prima la guard (blocca gli admin per id); poi la riga del
     * bersaglio, e su di essa si ricontrolla lo stato.
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
     * Blocca la riga e la rilegge dal DB. {@code findByIdForUpdate} nella stessa sessione restituirebbe
     * l'entità già caricata senza aggiornarla, e lo stato (in sospeso, eliminato) sarebbe quello precedente al lock.
     */
    private User lockFresh(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> NotFoundException.of("user", userId));
        entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE);
        Hibernate.initialize(user.getRoles()); // il refresh lascia i ruoli non caricati e la risposta li legge
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
