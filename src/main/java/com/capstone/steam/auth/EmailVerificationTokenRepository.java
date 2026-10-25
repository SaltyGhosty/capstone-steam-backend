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

    /** Solo l'utente proprietario del link (per bloccare la sua riga prima del link: ordine utente -> link). */
    @Query("select t.userId from EmailVerificationToken t where t.tokenHash = :tokenHash")
    Optional<Long> findUserIdByTokenHash(String tokenHash);

    /** Legge il link con lock in scrittura: due verifiche dello stesso link si alternano e la seconda lo vede già consumato. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from EmailVerificationToken t where t.tokenHash = :tokenHash")
    Optional<EmailVerificationToken> findByTokenHashForUpdate(String tokenHash);

    /** Tutti i link dell'utente (anche usati, annullati e scaduti), dal più recente al più vecchio. */
    List<EmailVerificationToken> findByUserIdOrderByIdDesc(Long userId);

    /** L'ultimo link emesso a questo utente (il reinvio ne copia i dati). */
    Optional<EmailVerificationToken> findFirstByUserIdOrderByIdDesc(Long userId);

    /** Link non usati e ancora validi di un utente, dal più recente al più vecchio. */
    List<EmailVerificationToken> findByUserIdAndUsedAtIsNullAndExpiresAtAfterOrderByIdDesc(Long userId, Instant now);

    /** Annulla i link ancora non usati di un utente (verificando con uno, gli altri smettono di valere). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update EmailVerificationToken t set t.usedAt = :now where t.userId = :userId and t.usedAt is null")
    int invalidateActive(Long userId, Instant now);

    /**
     * Conta una password errata su TUTTI i link della stessa registrazione (utente + password): i tentativi
     * sono per registrazione, non per link, quindi chiedere più link non dà più tentativi.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update EmailVerificationToken t set t.failedAttempts = t.failedAttempts + 1
             where t.userId = :userId and lower(t.username) = lower(:username) and t.passwordHash = :passwordHash""")
    int incrementFailedAttempts(Long userId, String username, String passwordHash);

    /** Annulla i link della registrazione se ha già raggiunto il massimo di password errate. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update EmailVerificationToken t set t.usedAt = :now
             where t.userId = :userId and lower(t.username) = lower(:username) and t.passwordHash = :passwordHash
               and t.usedAt is null and t.failedAttempts >= :max""")
    int invalidateIfTooManyFailures(Long userId, String username, String passwordHash, Instant now, int max);

    /** All'anonimizzazione dell'account: si cancellano tutti i suoi link di verifica. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from EmailVerificationToken t where t.userId = :userId")
    int deleteAllByUserId(Long userId);

    /** Segna il token come usato solo se era ancora inutilizzato; di due click simultanei solo uno riceve 1. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update EmailVerificationToken t set t.usedAt = :now where t.id = :id and t.usedAt is null")
    int markUsedIfUnused(Long id, Instant now);
}
