package com.capstone.steam.genre;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.capstone.steam.common.exception.BadRequestException;
import com.capstone.steam.common.exception.ConflictException;
import com.capstone.steam.common.exception.NotFoundException;
import com.capstone.steam.common.util.Slugs;
import com.capstone.steam.genre.dto.GenreRequest;
import com.capstone.steam.genre.dto.GenreResponse;

@Service
public class GenreService {

    private final GenreRepository genreRepository;

    public GenreService(GenreRepository genreRepository) {
        this.genreRepository = genreRepository;
    }

    @Transactional(readOnly = true)
    public List<GenreResponse> listAll() {
        return genreRepository.findAllByOrderByNameAsc().stream().map(GenreResponse::from).toList();
    }

    @Transactional
    public GenreResponse create(GenreRequest request) {
        String name = request.name().trim();
        String slug = Slugs.slugify(name);
        if (genreRepository.existsByNameIgnoreCase(name) || genreRepository.existsBySlug(slug)) {
            throw new ConflictException("genre.duplicate");
        }
        return GenreResponse.from(genreRepository.save(new Genre(name, slug)));
    }

    @Transactional
    public GenreResponse update(Long id, GenreRequest request) {
        Genre genre = genreRepository.findById(id).orElseThrow(() -> NotFoundException.of("genre", id));
        String name = request.name().trim();
        String slug = Slugs.slugify(name);
        genreRepository.findBySlug(slug)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new ConflictException("genre.duplicate");
                });
        genre.setName(name);
        genre.setSlug(slug);
        return GenreResponse.from(genre);
    }

    /** Borra el género; sus asociaciones con juegos caen por ON DELETE CASCADE. */
    @Transactional
    public void delete(Long id) {
        if (!genreRepository.existsById(id)) {
            throw NotFoundException.of("genre", id);
        }
        genreRepository.deleteById(id);
    }

    /** Para asignar géneros a un juego: todos los ids deben existir. */
    @Transactional(readOnly = true)
    public Set<Genre> resolve(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Set.of();
        }
        Set<Genre> found = new HashSet<>(genreRepository.findAllById(ids));
        if (found.size() != new HashSet<>(ids).size()) {
            throw new BadRequestException("genre.someMissing");
        }
        return found;
    }
}
