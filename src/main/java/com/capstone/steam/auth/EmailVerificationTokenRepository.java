package com.capstone.steam.auth;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    /** Solo el usuario dueño del enlace (para bloquear su fila antes que el enlace: orden usuario -> enlaces). */
    @Query("select t.userId from EmailVerificationToken t where t.tokenHash = :tokenHash")
    Optional<Long> findUserIdByTokenHash(String tokenHash);

    /** Lee el enlace con bloqueo de escritura: dos verificaciones del mismo enlace se turnan y la segunda lo ve ya gastado. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from EmailVerificationToken t where t.tokenHash = :tokenHash")
    Optional<EmailVerificationToken> findByTokenHashForUpdate(String tokenHash);

    /** Todos los enlaces del usuario (también usados, anulados y caducados), del más reciente al más antiguo. */
    List<EmailVerificationToken> findByUserIdOrderByIdDesc(Long userId);

    /** El último enlace emitido a este usuario (el reenvío copia sus datos). */
    Optional<EmailVerificationToken> findFirstByUserIdOrderByIdDesc(Long userId);

    /** Enlaces sin usar y aún vigentes de un usuario, del más reciente al más antiguo. */
    List<EmailVerificationToken> findByUserIdAndUsedAtIsNullAndExpiresAtAfterOrderByIdDesc(Long userId, Instant now);

    /** Anula los enlaces aún sin usar de un usuario (al verificar con uno, los demás dejan de valer). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update EmailVerificationToken t set t.usedAt = :now where t.userId = :userId and t.usedAt is null")
    int invalidateActive(Long userId, Instant now);

    /**
     * Cuenta una contraseña errónea en TODOS los enlaces del mismo registro (usuario + contraseña): los intentos
     * son por registro, no por enlace, así que pedir más enlaces no da más intentos.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update EmailVerificationToken t set t.failedAttempts = t.failedAttempts + 1
             where t.userId = :userId and lower(t.username) = lower(:username) and t.passwordHash = :passwordHash""")
    int incrementFailedAttempts(Long userId, String username, String passwordHash);

    /** Anula los enlaces del registro si ya llegó al máximo de contraseñas erróneas. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update EmailVerificationToken t set t.usedAt = :now
             where t.userId = :userId and lower(t.username) = lower(:username) and t.passwordHash = :passwordHash
               and t.usedAt is null and t.failedAttempts >= :max""")
    int invalidateIfTooManyFailures(Long userId, String username, String passwordHash, Instant now, int max);

    /** Marca el token como usado solo si seguía sin usar; de dos clics simultáneos solo uno recibe 1. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update EmailVerificationToken t set t.usedAt = :now where t.id = :id and t.usedAt is null")
    int markUsedIfUnused(Long id, Instant now);
}
