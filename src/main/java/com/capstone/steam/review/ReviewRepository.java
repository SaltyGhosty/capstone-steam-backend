package com.capstone.steam.review;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    @Query(value = "select r from Review r join fetch r.user where r.game.id = :gameId",
            countQuery = "select count(r) from Review r where r.game.id = :gameId")
    Page<Review> findByGameId(Long gameId, Pageable pageable);

    Optional<Review> findByUserIdAndGameId(Long userId, Long gameId);

    boolean existsByUserIdAndGameId(Long userId, Long gameId);
}
