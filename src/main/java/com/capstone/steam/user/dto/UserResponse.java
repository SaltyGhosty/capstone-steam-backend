package com.capstone.steam.user.dto;

import java.time.Instant;
import java.util.List;

import com.capstone.steam.user.Role;
import com.capstone.steam.user.User;

public record UserResponse(
        Long id,
        String username,
        String email,
        String displayName,
        String avatarUrl,
        String locale,
        boolean enabled,
        List<String> roles,
        Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                user.getLocale(),
                user.isEnabled(),
                user.getRoles().stream().map(Role::getName).map(Enum::name).sorted().toList(),
                user.getCreatedAt());
    }
}
