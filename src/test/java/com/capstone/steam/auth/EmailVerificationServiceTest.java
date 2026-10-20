package com.capstone.steam.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.capstone.steam.common.exception.BadRequestException;
import com.capstone.steam.common.exception.ConflictException;
import com.capstone.steam.user.Role;
import com.capstone.steam.user.RoleName;
import com.capstone.steam.user.RoleRepository;
import com.capstone.steam.user.User;
import com.capstone.steam.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-20T10:00:00Z");
    private static final PendingRegistration DATA = new PendingRegistration("nuevo", "hash-nuevo", "Nuevo", "en", true);

    @Mock private EmailVerificationTokenRepository tokens;
    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private ApplicationEventPublisher events;
    @Mock private AccountDataCheck dataCheck;

    private EmailVerificationService service;

    @BeforeEach
    void setUp() {
        service = new EmailVerificationService(tokens, userRepository, roleRepository, events,
                new EmailVerificationProperties(true, Duration.ofDays(1), 3), Clock.fixed(NOW, ZoneOffset.UTC),
                new ResendRateLimiter(Clock.fixed(NOW, ZoneOffset.UTC), 3), dataCheck);
    }

    private static User user(long id) {
        User user = new User("ana", "ana@test.com", "hash");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @Test
    void issueStoresOnlyTheHashAndTheRegistrationDataAndKeepsThePreviousLinks() {
        String raw = service.issue(user(5L), DATA);

        ArgumentCaptor<EmailVerificationToken> saved = ArgumentCaptor.forClass(EmailVerificationToken.class);
        verify(tokens).save(saved.capture());
        verify(tokens, never()).invalidateActive(anyLong(), any());
        assertThat(raw).hasSize(43);
        assertThat(saved.getValue().getTokenHash()).isEqualTo(TokenHashing.sha256Hex(raw)).isNotEqualTo(raw);
        assertThat(saved.getValue().getUserId()).isEqualTo(5L);
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(1)));
        assertThat(saved.getValue().pending()).isEqualTo(DATA);
    }

    @Test
    void verifyAppliesTheDataOfTheLinkAndInvalidatesTheOthers() {
        User ana = user(5L);
        EmailVerificationToken token = token(10L, 5L, NOW.plusSeconds(60));
        when(tokens.findByTokenHash(TokenHashing.sha256Hex("raw"))).thenReturn(Optional.of(token));
        when(userRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(ana));
        when(userRepository.findIdByUsernameIgnoreCase("nuevo")).thenReturn(Optional.empty());
        when(tokens.markUsedIfUnused(10L, NOW)).thenReturn(1);
        when(userRepository.findWithRolesById(5L)).thenReturn(Optional.of(ana));
        when(roleRepository.findByName(RoleName.ROLE_USER)).thenReturn(Optional.of(new Role(RoleName.ROLE_USER)));
        when(roleRepository.findByName(RoleName.ROLE_DEVELOPER)).thenReturn(Optional.of(new Role(RoleName.ROLE_DEVELOPER)));

        User verified = service.verify("raw");

        assertThat(verified).isSameAs(ana);
        assertThat(ana.isEmailVerified()).isTrue();
        assertThat(ana.getUsername()).isEqualTo("nuevo");
        assertThat(ana.getPasswordHash()).isEqualTo("hash-nuevo");
        assertThat(ana.getDisplayName()).isEqualTo("Nuevo");
        assertThat(ana.getLocale()).isEqualTo("en");
        assertThat(ana.hasRole(RoleName.ROLE_DEVELOPER)).isTrue();
        verify(tokens).invalidateActive(5L, NOW);
    }

    @Test
    void verifyFailsWith409AndKeepsTheLinkWhenAnotherAccountHasTheUsername() {
        EmailVerificationToken token = token(10L, 5L, NOW.plusSeconds(60));
        when(tokens.findByTokenHash(TokenHashing.sha256Hex("raw"))).thenReturn(Optional.of(token));
        when(userRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(user(5L)));
        when(userRepository.findIdByUsernameIgnoreCase("nuevo")).thenReturn(Optional.of(99L));

        assertThatThrownBy(() -> service.verify("raw")).isInstanceOf(ConflictException.class);
        verify(tokens, never()).markUsedIfUnused(anyLong(), any());
    }

    @Test
    void verifyRejectsAUsedToken() {
        EmailVerificationToken token = token(10L, 5L, NOW.plusSeconds(60));
        ReflectionTestUtils.setField(token, "usedAt", NOW.minusSeconds(5));
        when(tokens.findByTokenHash(TokenHashing.sha256Hex("raw"))).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.verify("raw")).isInstanceOf(BadRequestException.class)
                .hasMessageContaining("enlace");
        verify(tokens, never()).markUsedIfUnused(anyLong(), any());
    }

    @Test
    void verifyRejectsUnknownAndExpiredTokens() {
        when(tokens.findByTokenHash(TokenHashing.sha256Hex("nope"))).thenReturn(Optional.empty());
        when(tokens.findByTokenHash(TokenHashing.sha256Hex("old")))
                .thenReturn(Optional.of(token(11L, 5L, NOW)));  // caduca justo ahora: ya no vale

        assertThatThrownBy(() -> service.verify("nope")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.verify("old")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void verifyLosesTheRaceWhenAnotherRequestUsedTheTokenFirst() {
        EmailVerificationToken token = token(10L, 5L, NOW.plusSeconds(60));
        when(tokens.findByTokenHash(TokenHashing.sha256Hex("raw"))).thenReturn(Optional.of(token));
        when(userRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(user(5L)));
        when(userRepository.findIdByUsernameIgnoreCase("nuevo")).thenReturn(Optional.empty());
        when(tokens.markUsedIfUnused(10L, NOW)).thenReturn(0);

        assertThatThrownBy(() -> service.verify("raw")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void anOldAccountWithoutDataCanBeReclaimedButNotARecentOneOrOneWithDataOrAdminRole() {
        User old = user(1L);
        ReflectionTestUtils.setField(old, "createdAt", NOW.minus(Duration.ofDays(1)));
        when(dataCheck.hasData(1L)).thenReturn(false);
        assertThat(service.isReclaimable(old)).isTrue();

        User recent = user(2L);
        ReflectionTestUtils.setField(recent, "createdAt", NOW.minus(Duration.ofHours(23)));
        assertThat(service.isReclaimable(recent)).isFalse();

        User withData = user(3L);
        ReflectionTestUtils.setField(withData, "createdAt", NOW.minus(Duration.ofDays(2)));
        when(dataCheck.hasData(3L)).thenReturn(true);
        assertThat(service.isReclaimable(withData)).isFalse();

        User admin = user(4L);
        ReflectionTestUtils.setField(admin, "createdAt", NOW.minus(Duration.ofDays(2)));
        admin.addRole(new Role(RoleName.ROLE_ADMIN));
        assertThat(service.isReclaimable(admin)).isFalse();

        User verified = user(5L);
        ReflectionTestUtils.setField(verified, "createdAt", NOW.minus(Duration.ofDays(2)));
        verified.setEmailVerified(true);
        assertThat(service.isReclaimable(verified)).isFalse();
    }

    private static EmailVerificationToken token(long id, long userId, Instant expiresAt) {
        EmailVerificationToken token = new EmailVerificationToken(userId, "hash", expiresAt, DATA);
        ReflectionTestUtils.setField(token, "id", id);
        return token;
    }
}
