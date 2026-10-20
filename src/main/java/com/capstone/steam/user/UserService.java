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

    public UserService(UserRepository userRepository, RoleRepository roleRepository,
            RefreshTokenService refreshTokenService, AccessTokenRevocations accessTokenRevocations,
            EmailVerificationService emailVerificationService) {
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

    // ---------------------------- Administración ----------------------------

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> listUsers(Pageable pageable, String q) {
        // Los roles se cargan por lotes (hibernate.default_batch_fetch_size), no con un fetch join,
        // para que la paginación la haga la BD y no Hibernate en memoria.
        String term = q == null ? "" : q.trim();
        if (!term.isEmpty()) { // filtro opcional por usuario o email (lo usa el seed para encontrar una cuenta)
            return PageResponse.of(userRepository
                    .findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(term, term, pageable), UserResponse::from);
        }
        return PageResponse.of(userRepository.findAll(pageable), UserResponse::from);
    }

    @Transactional
    public UserResponse updateRoles(Long adminId, Long userId, Set<RoleName> requested) {
        Set<RoleName> roles = EnumSet.copyOf(requested);
        roles.add(RoleName.ROLE_USER); // todo usuario es al menos USER
        if (adminId.equals(userId) && !roles.contains(RoleName.ROLE_ADMIN)) {
            throw new BadRequestException("user.cannotRemoveOwnAdmin");
        }
        User user = findWithRoles(userId);
        user.getRoles().clear();
        roles.forEach(name -> user.addRole(roleRepository.findByName(name).orElseThrow()));
        // Los roles viajan en el access token: se cortan sus sesiones para que renueve con los nuevos
        refreshTokenService.revokeAll(userId);
        accessTokenRevocations.revokeUser(userId); // y el access token vigente deja de valer ya (D16)
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse setEnabled(Long adminId, Long userId, boolean enabled) {
        if (adminId.equals(userId) && !enabled) {
            throw new BadRequestException("user.cannotDisableSelf");
        }
        User user = findWithRoles(userId);
        user.setEnabled(enabled);
        if (!enabled) {
            refreshTokenService.revokeAll(userId);
            accessTokenRevocations.revokeUser(userId);
        }
        return UserResponse.from(user);
    }

    /** El admin da el email por verificado (soporte y seed), con los datos del último enlace. Idempotente. */
    @Transactional
    public UserResponse verifyEmail(Long userId) {
        return UserResponse.from(emailVerificationService.verifyByAdmin(userId));
    }

    private User findWithRoles(Long userId) {
        return userRepository.findWithRolesById(userId).orElseThrow(() -> NotFoundException.of("user", userId));
    }
}
