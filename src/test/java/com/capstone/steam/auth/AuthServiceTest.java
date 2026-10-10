package com.capstone.steam.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.capstone.steam.auth.dto.AccountType;
import com.capstone.steam.auth.dto.AuthResponse;
import com.capstone.steam.auth.dto.RegisterRequest;
import com.capstone.steam.common.exception.ConflictException;
import com.capstone.steam.notification.event.UserRegisteredEvent;
import com.capstone.steam.security.JwtService;
import com.capstone.steam.user.Role;
import com.capstone.steam.user.RoleName;
import com.capstone.steam.user.RoleRepository;
import com.capstone.steam.user.User;
import com.capstone.steam.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtService jwtService;
    @Mock private ApplicationEventPublisher events;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, roleRepository, passwordEncoder, authenticationManager,
                jwtService, events);
    }

    @Test
    void registerDeveloperHashesPasswordAssignsRolesAndPublishesEvent() {
        when(passwordEncoder.encode("secret123")).thenReturn("hashed");
        when(roleRepository.findByName(any())).thenAnswer(inv -> Optional.of(new Role(inv.getArgument(0))));
        when(jwtService.generateToken(any())).thenReturn("jwt");
        when(jwtService.getExpirationSeconds()).thenReturn(3600L);

        AuthResponse response = authService.register(
                new RegisterRequest("gaben", "Gabe@Valve.com", "secret123", null, AccountType.DEVELOPER, "en"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        User user = saved.getValue();
        assertThat(user.getPasswordHash()).isEqualTo("hashed");
        assertThat(user.getEmail()).isEqualTo("gabe@valve.com");
        assertThat(user.hasRole(RoleName.ROLE_USER)).isTrue();
        assertThat(user.hasRole(RoleName.ROLE_DEVELOPER)).isTrue();
        assertThat(user.hasRole(RoleName.ROLE_ADMIN)).isFalse();
        assertThat(user.getLocale()).isEqualTo("en");

        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue()).isInstanceOfSatisfying(UserRegisteredEvent.class,
                e -> assertThat(e.locale()).isEqualTo("en")); // el correo de bienvenida sale en su idioma
        assertThat(response.accessToken()).isEqualTo("jwt");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.user().locale()).isEqualTo("en");
        assertThat(response.user().roles()).containsExactly("ROLE_DEVELOPER", "ROLE_USER");
    }

    @Test
    void registerRejectsDuplicateEmailWithoutSavingOrPublishing() {
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.existsByUsernameIgnoreCase("newname")).thenReturn(false);
        when(userRepository.existsByEmail("taken@test.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("newname", "TAKEN@test.com", "secret123", null, null, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("email");

        verify(userRepository, never()).save(any());
        verify(events, never()).publishEvent(any(Object.class));
    }
}
