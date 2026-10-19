package com.capstone.steam.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.capstone.steam.common.exception.UnauthorizedException;
import com.capstone.steam.security.JwtProperties;
import com.capstone.steam.user.User;
import com.capstone.steam.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-19T10:00:00Z");
    private static final Duration LIFETIME = Duration.ofDays(7);

    @Mock private RefreshTokenRepository repository;
    @Mock private UserRepository userRepository;

    private RefreshTokenService service;
    private User user;
    private final AtomicLong ids = new AtomicLong(100);

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new RefreshTokenService(repository, userRepository,
                new JwtProperties("secret", Duration.ofMinutes(15), LIFETIME), clock);
        user = new User("gordon", "gordon@test.com", "hash");
        ReflectionTestUtils.setField(user, "id", 7L);
    }

    /** El repositorio simulado asigna ids como haría la BD. */
    private void saveAssignsIds() {
        when(repository.save(any(RefreshToken.class))).thenAnswer(inv -> {
            RefreshToken t = inv.getArgument(0);
            ReflectionTestUtils.setField(t, "id", ids.incrementAndGet());
            return t;
        });
    }

    private static String sha256(String raw) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void issueStoresOnlyTheSha256HashOfTheToken() throws Exception {
        saveAssignsIds();

        String raw = service.issue(user);

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(saved.capture());
        RefreshToken stored = saved.getValue();
        assertThat(stored.getTokenHash()).isNotEqualTo(raw).hasSize(64).isEqualTo(sha256(raw));
        assertThat(raw).hasSize(43).matches("[A-Za-z0-9_-]+"); // 32 bytes en base64url sin relleno
        assertThat(stored.getUserId()).isEqualTo(7L);
        assertThat(stored.getFamilyId()).hasSize(36);
        assertThat(stored.getExpiresAt()).isEqualTo(NOW.plus(LIFETIME));
        assertThat(stored.getRevokedAt()).isNull();
    }

    @Test
    void rotateRevokesTheOldTokenAndIssuesAnotherOfTheSameFamily() throws Exception {
        saveAssignsIds();
        String raw = service.issue(user);
        ArgumentCaptor<RefreshToken> first = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(first.capture());
        RefreshToken old = first.getValue();
        when(repository.findByTokenHash(sha256(raw))).thenReturn(Optional.of(old));
        when(userRepository.findWithRolesById(7L)).thenReturn(Optional.of(user));

        RefreshTokenService.Rotation rotation = service.rotate(raw);

        assertThat(rotation.user()).isSameAs(user);
        assertThat(rotation.refreshToken()).isNotEqualTo(raw);
        ArgumentCaptor<RefreshToken> all = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository, times(2)).save(all.capture());
        RefreshToken next = all.getAllValues().get(1);
        assertThat(next.getFamilyId()).isEqualTo(old.getFamilyId());
        assertThat(next.getTokenHash()).isEqualTo(sha256(rotation.refreshToken()));
        assertThat(old.getRevokedAt()).isEqualTo(NOW);
        assertThat(old.getReplacedById()).isEqualTo(next.getId());
    }

    @Test
    void rotatingAnAlreadyRevokedTokenRevokesTheWholeFamily() throws Exception {
        RefreshToken revoked = new RefreshToken(7L, sha256("old"), "family-1", NOW.plus(LIFETIME));
        revoked.revoke(NOW.minusSeconds(60), 5L);
        when(repository.findByTokenHash(sha256("old"))).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> service.rotate("old")).isInstanceOf(UnauthorizedException.class);

        verify(repository).revokeFamily("family-1", NOW);
        verify(repository, never()).save(any());
    }

    @Test
    void rotatingAnExpiredTokenIsRejected() throws Exception {
        RefreshToken expired = new RefreshToken(7L, sha256("exp"), "family-2", NOW.minusSeconds(1));
        when(repository.findByTokenHash(sha256("exp"))).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.rotate("exp")).isInstanceOf(UnauthorizedException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void unknownTokenIsRejected() {
        when(repository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotate("nope")).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void rotatingForADisabledUserRevokesAllHisTokens() throws Exception {
        RefreshToken token = new RefreshToken(7L, sha256("ok"), "family-3", NOW.plus(LIFETIME));
        user.setEnabled(false);
        when(repository.findByTokenHash(sha256("ok"))).thenReturn(Optional.of(token));
        when(userRepository.findWithRolesById(7L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.rotate("ok")).isInstanceOf(UnauthorizedException.class);

        verify(repository).revokeAllForUser(7L, NOW);
        verify(repository, never()).save(any());
    }

    @Test
    void revokeRevokesTheFamilyOfAKnownToken() throws Exception {
        RefreshToken token = new RefreshToken(7L, sha256("bye"), "family-4", NOW.plus(LIFETIME));
        when(repository.findByTokenHash(sha256("bye"))).thenReturn(Optional.of(token));

        service.revoke("bye");

        verify(repository).revokeFamily("family-4", NOW);
    }

    @Test
    void revokeIgnoresUnknownTokens() {
        when(repository.findByTokenHash(any())).thenReturn(Optional.empty());

        service.revoke("desconocido"); // idempotente: no lanza

        verify(repository, never()).revokeFamily(any(), any());
    }

    @Test
    void revokeAllDelegatesToTheRepository() {
        service.revokeAll(7L);

        verify(repository).revokeAllForUser(eq(7L), eq(NOW));
    }
}
