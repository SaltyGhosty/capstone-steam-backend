package com.capstone.steam.auth;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Revoca i token ancora vivi di una famiglia (riutilizzo rilevato o logout). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now where t.familyId = :familyId and t.revokedAt is null")
    int revokeFamily(String familyId, Instant now);

    /** Revoca tutte le sessioni di un utente (account disattivato o ruoli cambiati). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now where t.userId = :userId and t.revokedAt is null")
    int revokeAllForUser(Long userId, Instant now);

    /**
     * Revoca un token solo se è ancora vivo. È atomico: tra più refresh simultanei con lo stesso
     * token, solo uno riceve 1; gli altri ricevono 0 e vengono trattati come riutilizzo.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now where t.id = :id and t.revokedAt is null")
    int revokeIfActive(Long id, Instant now);

    /** Collega il token già revocato a quello che lo sostituisce. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshToken t set t.replacedById = :replacedById where t.id = :id")
    int linkReplacement(Long id, Long replacedById);

    /** All'anonimizzazione dell'account: si cancellano tutte le sue sessioni. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RefreshToken t where t.userId = :userId")
    int deleteAllByUserId(Long userId);

    /**
     * Pulizia per famiglie intere: cancella solo le famiglie i cui token sono scaduti tutti prima di
     * {@code cutoff}. Non cancella mai un token singolo di una famiglia con qualche token valido, perché
     * riutilizzare un token rubato continui ad abbattere la famiglia viva (D48).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            delete from RefreshToken t where t.familyId in (
                select r.familyId from RefreshToken r group by r.familyId having max(r.expiresAt) < :cutoff)""")
    int deleteExpiredFamilies(Instant cutoff);
}
