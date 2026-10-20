package com.capstone.steam.user;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.capstone.steam.common.dto.PageResponse;
import com.capstone.steam.security.AppUserPrincipal;
import com.capstone.steam.user.dto.UpdateEnabledRequest;
import com.capstone.steam.user.dto.UpdateRolesRequest;
import com.capstone.steam.user.dto.UserResponse;

import jakarta.validation.Valid;

/** Protegido por la regla /api/admin/** de SecurityConfig (solo ADMIN). */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public PageResponse<UserResponse> list(@PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return userService.listUsers(pageable);
    }

    @PutMapping("/{id}/roles")
    public UserResponse updateRoles(@AuthenticationPrincipal AppUserPrincipal admin, @PathVariable Long id,
            @Valid @RequestBody UpdateRolesRequest request) {
        return userService.updateRoles(admin.getId(), id, request.roles());
    }

    /** Da el email de la cuenta por verificado (soporte y seed). Idempotente. */
    @PostMapping("/{id}/verify-email")
    public UserResponse verifyEmail(@PathVariable Long id) {
        return userService.verifyEmail(id);
    }

    @PatchMapping("/{id}/enabled")
    public UserResponse setEnabled(@AuthenticationPrincipal AppUserPrincipal admin, @PathVariable Long id,
            @Valid @RequestBody UpdateEnabledRequest request) {
        return userService.setEnabled(admin.getId(), id, request.enabled());
    }
}
