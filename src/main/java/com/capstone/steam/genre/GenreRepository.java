package com.capstone.steam.genre;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GenreRepository extends JpaRepository<Genre, Long> {

    Optional<Genre> findBySlug(String slug);

    List<Genre> findAllByOrderByNameAsc();

    List<Genre> findBySlugIn(List<String> slugs);

    boolean existsBySlug(String slug);

    boolean existsByNameIgnoreCase(String name);
}
