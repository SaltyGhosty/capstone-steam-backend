package com.capstone.steam.auth;

import java.time.Clock;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.steam.common.exception.BadRequestException;
import com.capstone.steam.user.User;
import com.capstone.steam.user.UserRepository;

/**
 * Enlaces de verificación de email: de un solo uso, caducan a las 24 h (configurable) y en la BD solo
 * queda su SHA-256. El token en claro solo viaja en el evento que lleva el correo.
 */
@Service
public class EmailVerificationService {

    private final EmailVerificationTokenRepository tokens;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher events;
    private final EmailVerificationProperties properties;
    private final Clock clock;

    @Autowired
    public EmailVerificationService(EmailVerificationTokenRepository tokens, UserRepository userRepository,
            ApplicationEventPublisher events, EmailVerificationProperties properties, Clock clock) {
        this.tokens = tokens;
        this.userRepository = userRepository;
        this.events = events;
        this.properties = properties;
        this.clock = clock;
    }

    /** Emite un enlace nuevo, anula los anteriores del usuario y devuelve el token en claro. */
    @Transactional
    public String issue(User user) {
        Instant now = clock.instant();
        tokens.invalidateActive(user.getId(), now);
        String raw = TokenHashing.newRawToken();
        tokens.save(new EmailVerificationToken(user.getId(), TokenHashing.sha256Hex(raw), now.plus(properties.ttl())));
        return raw;
    }

    /**
     * Canjea el token: marca el email como verificado y devuelve el usuario (con roles).
     * Token inexistente, usado o caducado → 400 con el mismo texto (no se distingue cuál).
     */
    @Transactional
    public User verify(String rawToken) {
        Instant now = clock.instant();
        EmailVerificationToken token = tokens.findByTokenHash(TokenHashing.sha256Hex(rawToken))
                .orElseThrow(EmailVerificationService::invalid);
        if (token.isUsed() || !token.getExpiresAt().isAfter(now)) {
            throw invalid();
        }
        // Atómico: de dos clics simultáneos con el mismo enlace solo uno gana
        if (tokens.markUsedIfUnused(token.getId(), now) == 0) {
            throw invalid();
        }
        User user = userRepository.findWithRolesById(token.getUserId()).orElseThrow(EmailVerificationService::invalid);
        user.setEmailVerified(true);
        return user;
    }

    /** ¿Tiene la cuenta un enlace que todavía no ha caducado? */
    @Transactional(readOnly = true)
    public boolean hasLiveToken(Long userId) {
        return tokens.latestExpiry(userId).map(expiry -> expiry.isAfter(clock.instant())).orElse(false);
    }

    private static BadRequestException invalid() {
        return new BadRequestException("auth.verificationInvalid");
    }
}
