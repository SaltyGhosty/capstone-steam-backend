package com.capstone.steam.auth;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Token de verificación de email guardado solo como hash; de un solo uso. */
@Entity
@Table(name = "email_verification_tokens")
public class EmailVerificationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    /** SHA-256 en hex (64 caracteres) del token en claro. */
    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "used_at")
    private Instant usedAt;

    /** Datos del registro que generó este enlace (se aplican a la cuenta al verificar). */
    @Column(name = "username", nullable = false, updatable = false, length = 50)
    private String username;

    @Column(name = "password_hash", nullable = false, updatable = false, length = 100)
    private String passwordHash;

    @Column(name = "display_name", updatable = false, length = 100)
    private String displayName;

    @Column(name = "locale", nullable = false, updatable = false, length = 5)
    private String locale;

    @Column(name = "developer", nullable = false, updatable = false)
    private boolean developer;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    protected EmailVerificationToken() {
    }

    public EmailVerificationToken(Long userId, String tokenHash, Instant expiresAt, PendingRegistration data) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.username = data.username();
        this.passwordHash = data.passwordHash();
        this.displayName = data.displayName();
        this.locale = data.locale();
        this.developer = data.developer();
    }

    /** Los datos del registro que guarda este enlace. */
    public PendingRegistration pending() {
        return new PendingRegistration(username, passwordHash, displayName, locale, developer);
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getUsername() {
        return username;
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }
}
