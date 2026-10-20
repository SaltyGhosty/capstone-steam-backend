package com.capstone.steam.user;

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
    Optional<User> findWithRolesByUsernameIgnoreCase(String username);

    /**
     * Búsqueda de jugadores (activos y sin rol DEVELOPER ni ADMIN) por username o nombre visible.
     * {@code pattern} ya viene con los comodines {@code %} y con los {@code %}/{@code _} del usuario
     * escapados con {@code !}; va siempre como parámetro, nunca concatenado.
     */
    @Query("""
            select u from User u
             where u.enabled = true and u.emailVerified = true and u.id <> :excludeId
               and (lower(u.username) like lower(:pattern) escape '!'
                    or lower(u.displayName) like lower(:pattern) escape '!')
               and not exists (select r.id from u.roles r
                                where r.name in (com.capstone.steam.user.RoleName.ROLE_DEVELOPER,
                                                 com.capstone.steam.user.RoleName.ROLE_ADMIN))
             order by lower(u.username)""")
    List<User> searchPlayers(String pattern, Long excludeId, Pageable pageable);

    Optional<User> findByEmail(String email);

    org.springframework.data.domain.Page<User> findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(
            String username, String email, Pageable pageable);

    /** Ids por email y por usuario, sin cargar la fila: así se pueden bloquear en orden de id (sin interbloqueos). */
    @Query("select u.id from User u where u.email = :email")
    Optional<Long> findIdByEmail(String email);

    @Query("select u.id from User u where lower(u.username) = lower(:username)")
    Optional<Long> findIdByUsernameIgnoreCase(String username);

    /** Lee la fila con bloqueo de escritura: registro y verificación no se pisan sobre la misma cuenta. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(Long id);

    Optional<User> findByUsernameIgnoreCase(String username);

    boolean existsByEmail(String email);

    boolean existsByUsernameIgnoreCase(String username);
}
