package com.capstone.steam.user;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.capstone.steam.security.AppUserPrincipal;
import com.capstone.steam.user.dto.AccountDeletionResponse;
import com.capstone.steam.user.dto.DeletionRequest;
import com.capstone.steam.user.dto.UpdateProfileRequest;
import com.capstone.steam.user.dto.UserResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/users/me")
public class UserController {

    private final UserService userService;
    private final AccountDeletionService accountDeletionService;

    public UserController(UserService userService, AccountDeletionService accountDeletionService) {
        this.userService = userService;
        this.accountDeletionService = accountDeletionService;
    }

    @GetMapping
    public UserResponse me(@AuthenticationPrincipal AppUserPrincipal principal) {
        return userService.getProfile(principal.getId());
    }

    @PatchMapping
    public UserResponse updateMe(@AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(principal.getId(), request);
    }

    /** Pide la baja de la propia cuenta: queda en pausa 14 días y luego se anonimiza. */
    @PostMapping("/deletion")
    public AccountDeletionResponse requestDeletion(@AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody DeletionRequest request) {
        return new AccountDeletionResponse(accountDeletionService.requestOwn(principal.getId(), request.password()));
    }
}
