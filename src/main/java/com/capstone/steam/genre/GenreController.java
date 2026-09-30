package com.capstone.steam.genre;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.capstone.steam.genre.dto.GenreRequest;
import com.capstone.steam.genre.dto.GenreResponse;

import jakarta.validation.Valid;

/** GET público; escritura solo ADMIN (regla /api/admin/** de SecurityConfig). */
@RestController
public class GenreController {

    private final GenreService genreService;

    public GenreController(GenreService genreService) {
        this.genreService = genreService;
    }

    @GetMapping("/api/genres")
    public List<GenreResponse> list() {
        return genreService.listAll();
    }

    @PostMapping("/api/admin/genres")
    @ResponseStatus(HttpStatus.CREATED)
    public GenreResponse create(@Valid @RequestBody GenreRequest request) {
        return genreService.create(request);
    }

    @PutMapping("/api/admin/genres/{id}")
    public GenreResponse update(@PathVariable Long id, @Valid @RequestBody GenreRequest request) {
        return genreService.update(id, request);
    }

    @DeleteMapping("/api/admin/genres/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        genreService.delete(id);
    }
}
