package com.capstone.steam.review;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    @Query(value = "select r from Review r join fetch r.user where r.game.id = :gameId",
            countQuery = "select count(r) from Review r where r.game.id = :gameId")
    Page<Review> findByGameId(Long gameId, Pageable pageable);

    @Query(value = "select r from Review r join fetch r.user where r.game.id = :gameId and r.language = :language",
            countQuery = "select count(r) from Review r where r.game.id = :gameId and r.language = :language")
    Page<Review> findByGameIdAndLanguage(Long gameId, String language, Pageable pageable);

    /** Lee la reseña bloqueándola: update y delete leen el voto que luego usan para el delta. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Review r where r.id = :id")
    Optional<Review> findByIdForUpdate(Long id);

    Optional<Review> findByUserIdAndGameId(Long userId, Long gameId);

    boolean existsByUserIdAndGameId(Long userId, Long gameId);
}
