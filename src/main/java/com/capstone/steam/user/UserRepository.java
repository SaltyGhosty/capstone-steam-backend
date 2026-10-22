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

    /** Login con username o email; carga los roles en la misma query. */
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
     * Búsqueda de jugadores (activos y sin rol DEVELOPER ni ADMIN) por username o nombre visible.
     * {@code pattern} ya viene con los comodines {@code %} y con los {@code %}/{@code _} del usuario
     * escapados con {@code !}; va siempre como parámetro, nunca concatenado.
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

    /** Listado del panel: sin las cuentas ya anonimizadas. */
    org.springframework.data.domain.Page<User> findByDeletedAtIsNull(Pageable pageable);

    /** {@code pattern} viene de {@code LikePatterns.contains}. */
    @Query("""
            select u from User u
             where u.deletedAt is null
               and (lower(u.username) like lower(:pattern) escape '!'
                    or lower(u.email) like lower(:pattern) escape '!')""")
    org.springframework.data.domain.Page<User> searchNotDeleted(String pattern, Pageable pageable);

    /** Ids por email y por usuario, sin cargar la fila: así se pueden bloquear en orden de id (sin interbloqueos). */
    @Query("select u.id from User u where u.email = :email")
    Optional<Long> findIdByEmail(String email);

    @Query("select u.id from User u where lower(u.username) = lower(:username)")
    Optional<Long> findIdByUsernameIgnoreCase(String username);

    /** Lee la fila con bloqueo de escritura: registro y verificación no se pisan sobre la misma cuenta. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(Long id);

    /**
     * Los administradores que cuentan (activos, sin baja programada ni anonimizados), bloqueados por id
     * ascendente: dos peticiones que quitan admins a la vez se serializan sin interbloqueos.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select u from User u
             where u.enabled = true and u.deletionScheduledAt is null and u.deletedAt is null
               and exists (select r.id from u.roles r where r.name = com.capstone.steam.user.RoleName.ROLE_ADMIN)
             order by u.id""")
    List<User> lockCountingAdmins();

    /** Cuántos administradores que cuentan hay sin contar a {@code excludeId}; sin bloqueo (ver {@code AdminGuard}). */
    @Query("""
            select count(u) from User u
             where u.enabled = true and u.deletionScheduledAt is null and u.deletedAt is null and u.id <> :excludeId
               and exists (select r.id from u.roles r where r.name = com.capstone.steam.user.RoleName.ROLE_ADMIN)""")
    long countCountingAdminsExcluding(Long excludeId);

    /** Las cuentas cuyo plazo ya venció y siguen sin anonimizar, por id (la tarea programada las recorre en ese orden). */
    @Query("""
            select u.id from User u
             where u.deletionScheduledAt <= :now and u.deletedAt is null
             order by u.id""")
    List<Long> findIdsDueForDeletion(Instant now);

    Optional<User> findByUsernameIgnoreCase(String username);

    boolean existsByEmail(String email);

    boolean existsByUsernameIgnoreCase(String username);
}
