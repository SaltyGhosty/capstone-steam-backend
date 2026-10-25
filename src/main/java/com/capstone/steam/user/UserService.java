package com.capstone.steam.user;

import java.util.EnumSet;
import java.util.Set;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.steam.auth.EmailVerificationService;
import com.capstone.steam.auth.RefreshTokenService;
import com.capstone.steam.common.dto.PageResponse;
import com.capstone.steam.common.exception.BadRequestException;
import com.capstone.steam.common.exception.NotFoundException;
import com.capstone.steam.common.util.LikePatterns;
import com.capstone.steam.security.AccessTokenRevocations;
import com.capstone.steam.user.dto.UpdateProfileRequest;
import com.capstone.steam.user.dto.UserResponse;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenService refreshTokenService;
    private final AccessTokenRevocations accessTokenRevocations;
    private final EmailVerificationService emailVerificationService;
    private final AdminGuard adminGuard;

    public UserService(UserRepository userRepository, RoleRepository roleRepository,
            RefreshTokenService refreshTokenService, AccessTokenRevocations accessTokenRevocations,
            EmailVerificationService emailVerificationService, AdminGuard adminGuard) {
        this.adminGuard = adminGuard;
        this.emailVerificationService = emailVerificationService;
        this.accessTokenRevocations = accessTokenRevocations;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional(readOnly = true)
    public UserResponse getProfile(Long userId) {
        return UserResponse.from(findWithRoles(userId));
    }

    @Transactional
    public UserResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = findWithRoles(userId);
        if (request.displayName() != null) {
            user.setDisplayName(request.displayName().trim());
        }
        if (request.avatarUrl() != null) {
            user.setAvatarUrl(request.avatarUrl().isBlank() ? null : request.avatarUrl().trim());
        }
        if (request.locale() != null) {
            user.setLocale(request.locale());
        }
        if (request.profilePublic() != null) {
            user.setProfilePublic(request.profilePublic());
        }
        return UserResponse.from(user);
    }

    // ---------------------------- Amministrazione ----------------------------

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> listUsers(Pageable pageable, String q) {
        // I ruoli si caricano a lotti (hibernate.default_batch_fetch_size), non con un fetch join,
        // perché la paginazione la faccia il DB e non Hibernate in memoria.
        String term = q == null ? "" : q.trim();
        if (!term.isEmpty()) { // filtro opzionale per username o email (lo usa il seed per trovare un account)
            return PageResponse.of(userRepository.searchNotDeleted(LikePatterns.contains(term), pageable), UserResponse::from);
        }
        return PageResponse.of(userRepository.findByDeletedAtIsNull(pageable), UserResponse::from);
    }

    @Transactional
    public UserResponse updateRoles(Long adminId, Long userId, Set<RoleName> requested) {
        Set<RoleName> roles = EnumSet.copyOf(requested);
        roles.add(RoleName.ROLE_USER); // ogni utente è almeno USER
        if (adminId.equals(userId) && !roles.contains(RoleName.ROLE_ADMIN)) {
            throw new BadRequestException("user.cannotRemoveOwnAdmin");
        }
        User user = findLive(userId);
        if (user.hasRole(RoleName.ROLE_ADMIN) && !roles.contains(RoleName.ROLE_ADMIN)) {
            adminGuard.requireAnotherAdmin(userId); // non si può lasciare lo shop senza amministratori
        }
        user.getRoles().clear();
        roles.forEach(name -> user.addRole(roleRepository.findByName(name).orElseThrow()));
        // I ruoli viaggiano nell'access token: si chiudono le sue sessioni perché rinnovi con i nuovi
        refreshTokenService.revokeAll(userId);
        accessTokenRevocations.revokeUser(userId); // e l'access token in corso smette subito di valere (D16)
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse setEnabled(Long adminId, Long userId, boolean enabled) {
        if (adminId.equals(userId) && !enabled) {
            throw new BadRequestException("user.cannotDisableSelf");
        }
        User user = findLive(userId);
        if (!enabled && user.hasRole(RoleName.ROLE_ADMIN)) {
            adminGuard.requireAnotherAdmin(userId);
        }
        user.setEnabled(enabled);
        if (!enabled) {
            refreshTokenService.revokeAll(userId);
            accessTokenRevocations.revokeUser(userId);
        }
        return UserResponse.from(user);
    }

    /** L'admin segna l'email come verificata (supporto e seed), con i dati dell'ultimo link. Idempotente. */
    @Transactional
    public UserResponse verifyEmail(Long userId) {
        return UserResponse.from(emailVerificationService.verifyByAdmin(userId));
    }

    private User findWithRoles(Long userId) {
        return userRepository.findWithRolesById(userId).orElseThrow(() -> NotFoundException.of("user", userId));
    }

    /** Per le mutazioni dell'admin: un account eliminato (anonimizzato) per lui non esiste più. */
    private User findLive(Long userId) {
        User user = findWithRoles(userId);
        if (user.isDeleted()) {
            throw NotFoundException.of("user", userId);
        }
        return user;
    }
}
