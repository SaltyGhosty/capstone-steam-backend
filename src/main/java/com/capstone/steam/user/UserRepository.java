package com.capstone.steam.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, Long> {

    /** Login con username o email; carga los roles en la misma query. */
    @EntityGraph(attributePaths = "roles")
    @Query("select u from User u where lower(u.username) = lower(:login) or u.email = lower(:login)")
    Optional<User> findByLogin(String login);

    @EntityGraph(attributePaths = "roles")
    Optional<User> findWithRolesById(Long id);

    boolean existsByEmail(String email);

    boolean existsByUsernameIgnoreCase(String username);
}
