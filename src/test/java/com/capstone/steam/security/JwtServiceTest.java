package com.capstone.steam.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;

class JwtServiceTest {

    private static final String SECRET = Base64.getEncoder()
            .encodeToString("una-clave-de-prueba-de-mas-de-32-bytes!!".getBytes());

    private final JwtService jwtService = new JwtService(new JwtProperties(SECRET, Duration.ofMinutes(10)));

    @Test
    void generatedTokenRoundTripsIdUsernameAndRoles() {
        var user = AppUserPrincipal.fromToken(42L, "gordon", List.of("ROLE_USER", "ROLE_DEVELOPER"));

        AppUserPrincipal parsed = jwtService.parseToken(jwtService.generateToken(user));

        assertThat(parsed.getId()).isEqualTo(42L);
        assertThat(parsed.getUsername()).isEqualTo("gordon");
        assertThat(parsed.getRoles()).containsExactly("ROLE_USER", "ROLE_DEVELOPER");
        assertThat(jwtService.getExpirationSeconds()).isEqualTo(600);
    }

    @Test
    void rejectsTamperedToken() {
        String token = jwtService.generateToken(AppUserPrincipal.fromToken(1L, "a", List.of("ROLE_USER")));
        String[] parts = token.split("\\.");
        // payload distinto con la firma original
        String forgedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"1\",\"roles\":[\"ROLE_ADMIN\"]}".getBytes());
        String forged = parts[0] + "." + forgedPayload + "." + parts[2];

        assertThatThrownBy(() -> jwtService.parseToken(forged)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTokenSignedWithAnotherKey() {
        String otherSecret = Base64.getEncoder().encodeToString("otra-clave-distinta-de-mas-de-32-bytes!!".getBytes());
        String token = new JwtService(new JwtProperties(otherSecret, Duration.ofMinutes(10)))
                .generateToken(AppUserPrincipal.fromToken(1L, "a", List.of("ROLE_USER")));

        assertThatThrownBy(() -> jwtService.parseToken(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsExpiredToken() {
        var expiredIssuer = new JwtService(new JwtProperties(SECRET, Duration.ofSeconds(-5)));
        String token = expiredIssuer.generateToken(AppUserPrincipal.fromToken(1L, "a", List.of("ROLE_USER")));

        assertThatThrownBy(() -> jwtService.parseToken(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void failsFastWithMissingOrShortSecret() {
        assertThatThrownBy(() -> new JwtService(new JwtProperties("", Duration.ofMinutes(1))))
                .isInstanceOf(IllegalStateException.class);
        String shortSecret = Base64.getEncoder().encodeToString("corta".getBytes());
        assertThatThrownBy(() -> new JwtService(new JwtProperties(shortSecret, Duration.ofMinutes(1))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }
}
