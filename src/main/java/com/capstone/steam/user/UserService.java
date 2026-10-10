package com.capstone.steam.user;

import java.util.EnumSet;
import java.util.Set;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.steam.common.dto.PageResponse;
import com.capstone.steam.common.exception.BadRequestException;
import com.capstone.steam.common.exception.NotFoundException;
import com.capstone.steam.user.dto.UpdateProfileRequest;
import com.capstone.steam.user.dto.UserResponse;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    public UserService(UserRepository userRepository, RoleRepository roleRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
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
        return UserResponse.from(user);
    }

    // ---------------------------- Administración ----------------------------

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> listUsers(Pageable pageable) {
        // Los roles se cargan por lotes (hibernate.default_batch_fetch_size), no con un fetch join,
        // para que la paginación la haga la BD y no Hibernate en memoria.
        return PageResponse.of(userRepository.findAll(pageable), UserResponse::from);
    }

    @Transactional
    public UserResponse updateRoles(Long adminId, Long userId, Set<RoleName> requested) {
        Set<RoleName> roles = EnumSet.copyOf(requested);
        roles.add(RoleName.ROLE_USER); // todo usuario es al menos USER
        if (adminId.equals(userId) && !roles.contains(RoleName.ROLE_ADMIN)) {
            throw new BadRequestException("No puedes quitarte a ti mismo el rol de administrador");
        }
        User user = findWithRoles(userId);
        user.getRoles().clear();
        roles.forEach(name -> user.addRole(roleRepository.findByName(name).orElseThrow()));
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse setEnabled(Long adminId, Long userId, boolean enabled) {
        if (adminId.equals(userId) && !enabled) {
            throw new BadRequestException("No puedes desactivar tu propia cuenta");
        }
        User user = findWithRoles(userId);
        user.setEnabled(enabled);
        return UserResponse.from(user);
    }

    private User findWithRoles(Long userId) {
        return userRepository.findWithRolesById(userId).orElseThrow(() -> NotFoundException.of("Usuario", userId));
    }
}
