package com.capstone.steam.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.capstone.steam.user.Role;
import com.capstone.steam.user.User;

/**
 * Usuario autenticado. En el login se construye desde la BD (con el hash);
 * en cada petición se reconstruye desde el JWT (sin hash y sin tocar la BD).
 * Los controllers lo reciben con {@code @AuthenticationPrincipal}.
 */
public final class AppUserPrincipal implements UserDetails {

    private final Long id;
    private final String username;
    private final String password;
    private final boolean enabled;
    private final List<GrantedAuthority> authorities;

    private AppUserPrincipal(Long id, String username, String password, boolean enabled, List<String> roles) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.enabled = enabled;
        this.authorities = roles.stream().<GrantedAuthority>map(SimpleGrantedAuthority::new).toList();
    }

    public static AppUserPrincipal fromUser(User user) {
        List<String> roles = user.getRoles().stream().map(Role::getName).map(Enum::name).sorted().toList();
        return new AppUserPrincipal(user.getId(), user.getUsername(), user.getPasswordHash(), user.isEnabled(), roles);
    }

    public static AppUserPrincipal fromToken(Long id, String username, List<String> roles) {
        return new AppUserPrincipal(id, username, null, true, roles);
    }

    public Long getId() {
        return id;
    }

    public List<String> getRoles() {
        return authorities.stream().map(GrantedAuthority::getAuthority).toList();
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }
}
