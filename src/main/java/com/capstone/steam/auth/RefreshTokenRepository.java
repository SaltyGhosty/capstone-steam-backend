package com.capstone.steam.auth;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Revoca los tokens aún vivos de una familia (reutilización detectada o cierre de sesión). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now where t.familyId = :familyId and t.revokedAt is null")
    int revokeFamily(String familyId, Instant now);

    /** Revoca todas las sesiones de un usuario (cuenta desactivada o roles cambiados). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now where t.userId = :userId and t.revokedAt is null")
    int revokeAllForUser(Long userId, Instant now);

    /**
     * Revoca un token solo si sigue vivo. Es atómico: de varios refresh simultáneos con el mismo
     * token, solo uno recibe 1; el resto recibe 0 y se trata como reutilización.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now where t.id = :id and t.revokedAt is null")
    int revokeIfActive(Long id, Instant now);

    /** Apunta el token ya revocado al que lo sustituye. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshToken t set t.replacedById = :replacedById where t.id = :id")
    int linkReplacement(Long id, Long replacedById);

    /** Al anonimizar la cuenta: se borran todas sus sesiones. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RefreshToken t where t.userId = :userId")
    int deleteAllByUserId(Long userId);

    /**
     * Purga por familias enteras: solo borra las familias cuyos tokens caducaron todos antes de
     * {@code cutoff}. Nunca borra un token suelto de una familia con algún token vigente, para que
     * reutilizar un token robado siga tirando la familia viva (D48).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            delete from RefreshToken t where t.familyId in (
                select r.familyId from RefreshToken r group by r.familyId having max(r.expiresAt) < :cutoff)""")
    int deleteExpiredFamilies(Instant cutoff);
}
