package com.capstone.steam.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.capstone.steam.common.exception.BadRequestException;
import com.capstone.steam.user.User;
import com.capstone.steam.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-20T10:00:00Z");

    @Mock private EmailVerificationTokenRepository tokens;
    @Mock private UserRepository userRepository;
    @Mock private ApplicationEventPublisher events;

    private EmailVerificationService service;

    @BeforeEach
    void setUp() {
        service = new EmailVerificationService(tokens, userRepository, events,
                new EmailVerificationProperties(true, Duration.ofDays(1), 3), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static User user(long id) {
        User user = new User("ana", "ana@test.com", "hash");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @Test
    void issueInvalidatesThePreviousLinksAndStoresOnlyTheHash() {
        String raw = service.issue(user(5L));

        InOrder order = inOrder(tokens);
        order.verify(tokens).invalidateActive(5L, NOW);
        ArgumentCaptor<EmailVerificationToken> saved = ArgumentCaptor.forClass(EmailVerificationToken.class);
        order.verify(tokens).save(saved.capture());

        assertThat(raw).hasSize(43);
        assertThat(saved.getValue().getTokenHash()).isEqualTo(TokenHashing.sha256Hex(raw)).isNotEqualTo(raw);
        assertThat(saved.getValue().getUserId()).isEqualTo(5L);
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(1)));
    }

    @Test
    void verifyMarksTheTokenUsedAndVerifiesTheUser() {
        User ana = user(5L);
        EmailVerificationToken token = token(10L, 5L, NOW.plusSeconds(60));
        when(tokens.findByTokenHash(TokenHashing.sha256Hex("raw"))).thenReturn(Optional.of(token));
        when(tokens.markUsedIfUnused(10L, NOW)).thenReturn(1);
        when(userRepository.findWithRolesById(5L)).thenReturn(Optional.of(ana));

        User verified = service.verify("raw");

        assertThat(verified).isSameAs(ana);
        assertThat(ana.isEmailVerified()).isTrue();
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
        when(tokens.markUsedIfUnused(10L, NOW)).thenReturn(0);

        assertThatThrownBy(() -> service.verify("raw")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void hasLiveTokenComparesTheLatestExpiryWithNow() {
        when(tokens.latestExpiry(1L)).thenReturn(Optional.of(NOW.plusSeconds(1)));
        when(tokens.latestExpiry(2L)).thenReturn(Optional.of(NOW));
        when(tokens.latestExpiry(3L)).thenReturn(Optional.empty());

        assertThat(service.hasLiveToken(1L)).isTrue();
        assertThat(service.hasLiveToken(2L)).isFalse();
        assertThat(service.hasLiveToken(3L)).isFalse();
    }

    private static EmailVerificationToken token(long id, long userId, Instant expiresAt) {
        EmailVerificationToken token = new EmailVerificationToken(userId, "hash", expiresAt);
        ReflectionTestUtils.setField(token, "id", id);
        return token;
    }
}
