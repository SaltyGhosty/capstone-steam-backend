package com.capstone.steam.auth;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    /** El último enlace emitido a este usuario (el reenvío copia sus datos). */
    Optional<EmailVerificationToken> findFirstByUserIdOrderByIdDesc(Long userId);

    /** Anula los enlaces aún sin usar de un usuario (al verificar con uno, los demás dejan de valer). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update EmailVerificationToken t set t.usedAt = :now where t.userId = :userId and t.usedAt is null")
    int invalidateActive(Long userId, Instant now);

    /** Marca el token como usado solo si seguía sin usar; de dos clics simultáneos solo uno recibe 1. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update EmailVerificationToken t set t.usedAt = :now where t.id = :id and t.usedAt is null")
    int markUsedIfUnused(Long id, Instant now);
}
