package com.capstone.steam.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

/** Emite y valida access tokens HS256. Claims: sub = id del usuario, username y roles. */
@Service
public class JwtService {

    private static final String USERNAME_CLAIM = "username";
    private static final String ROLES_CLAIM = "roles";

    private final SecretKey key;
    private final Duration expiration;

    public JwtService(JwtProperties properties) {
        if (properties.secret() == null || properties.secret().isBlank()) {
            throw new IllegalStateException("Falta app.jwt.secret: define JWT_SECRET en el entorno o en .env");
        }
        byte[] keyBytes = Decoders.BASE64.decode(properties.secret());
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET debe tener al menos 32 bytes (256 bits) tras decodificar el Base64");
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.expiration = properties.expiration();
    }

    public String generateToken(AppUserPrincipal user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim(USERNAME_CLAIM, user.getUsername())
                .claim(ROLES_CLAIM, user.getRoles())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(key)
                .compact();
    }

    /**
     * @throws JwtException si la firma no es válida, el token ha caducado o está mal formado
     */
    public AppUserPrincipal parseToken(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        List<?> rawRoles = claims.get(ROLES_CLAIM, List.class);
        List<String> roles = rawRoles == null ? List.of() : rawRoles.stream().map(String::valueOf).toList();
        return AppUserPrincipal.fromToken(Long.valueOf(claims.getSubject()), claims.get(USERNAME_CLAIM, String.class), roles);
    }

    public long getExpirationSeconds() {
        return expiration.toSeconds();
    }
}
