package com.capstone.steam.genre.dto;

import com.capstone.steam.genre.Genre;

public record GenreResponse(Long id, String name, String slug) {

    public static GenreResponse from(Genre genre) {
        return new GenreResponse(genre.getId(), genre.getName(), genre.getSlug());
    }
}
