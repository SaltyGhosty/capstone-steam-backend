package com.capstone.steam.user;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, Long> {

    /** Login con username o email; carica i ruoli nella stessa query. */
    @EntityGraph(attributePaths = "roles")
    @Query("select u from User u where lower(u.username) = lower(:login) or u.email = lower(:login)")
    Optional<User> findByLogin(String login);

    @EntityGraph(attributePaths = "roles")
    Optional<User> findWithRolesById(Long id);

    @EntityGraph(attributePaths = "roles")
    @Query("select u from User u where u.id in :ids")
    List<User> findAllWithRolesByIdIn(java.util.Collection<Long> ids);

    @EntityGraph(attributePaths = "roles")
    Optional<User> findWithRolesByUsernameIgnoreCase(String username);

    /**
     * Ricerca di giocatori (attivi e senza ruolo DEVELOPER né ADMIN) per username o nome visibile.
     * {@code pattern} arriva già con i caratteri jolly {@code %} e con i {@code %}/{@code _} dell'utente
     * escapati con {@code !}; va sempre come parametro, mai concatenato.
     */
    @Query("""
            select u from User u
             where u.enabled = true and u.emailVerified = true and u.id <> :excludeId
               and u.deletionScheduledAt is null and u.deletedAt is null
               and (lower(u.username) like lower(:pattern) escape '!'
                    or lower(u.displayName) like lower(:pattern) escape '!')
               and not exists (select r.id from u.roles r
                                where r.name in (com.capstone.steam.user.RoleName.ROLE_DEVELOPER,
                                                 com.capstone.steam.user.RoleName.ROLE_ADMIN))
             order by lower(u.username)""")
    List<User> searchPlayers(String pattern, Long excludeId, Pageable pageable);

    Optional<User> findByEmail(String email);

    /** Elenco del pannello: senza gli account già anonimizzati. */
    org.springframework.data.domain.Page<User> findByDeletedAtIsNull(Pageable pageable);

    /** {@code pattern} proviene da {@code LikePatterns.contains}. */
    @Query("""
            select u from User u
             where u.deletedAt is null
               and (lower(u.username) like lower(:pattern) escape '!'
                    or lower(u.email) like lower(:pattern) escape '!')""")
    org.springframework.data.domain.Page<User> searchNotDeleted(String pattern, Pageable pageable);

    /** Id per email e per username, senza caricare la riga: così si possono bloccare in ordine di id (senza deadlock). */
    @Query("select u.id from User u where u.email = :email")
    Optional<Long> findIdByEmail(String email);

    @Query("select u.id from User u where lower(u.username) = lower(:username)")
    Optional<Long> findIdByUsernameIgnoreCase(String username);

    /** Legge la riga con lock in scrittura: registrazione e verifica non si pestano i piedi sullo stesso account. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(Long id);

    /**
     * Gli amministratori che contano (attivi, senza cancellazione programmata né anonimizzati), bloccati per id
     * crescente: due richieste che tolgono admin contemporaneamente si serializzano senza deadlock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select u from User u
             where u.enabled = true and u.deletionScheduledAt is null and u.deletedAt is null
               and exists (select r.id from u.roles r where r.name = com.capstone.steam.user.RoleName.ROLE_ADMIN)
             order by u.id""")
    List<User> lockCountingAdmins();

    /** Quanti amministratori che contano ci sono senza contare {@code excludeId}; senza lock (vedi {@code AdminGuard}). */
    @Query("""
            select count(u) from User u
             where u.enabled = true and u.deletionScheduledAt is null and u.deletedAt is null and u.id <> :excludeId
               and exists (select r.id from u.roles r where r.name = com.capstone.steam.user.RoleName.ROLE_ADMIN)""")
    long countCountingAdminsExcluding(Long excludeId);

    /** Gli account il cui periodo è scaduto e ancora non anonimizzati, per id (il task programmato li scorre in quest'ordine). */
    @Query("""
            select u.id from User u
             where u.deletionScheduledAt <= :now and u.deletedAt is null
             order by u.id""")
    List<Long> findIdsDueForDeletion(Instant now);

    Optional<User> findByUsernameIgnoreCase(String username);

    boolean existsByEmail(String email);

    boolean existsByUsernameIgnoreCase(String username);
}
