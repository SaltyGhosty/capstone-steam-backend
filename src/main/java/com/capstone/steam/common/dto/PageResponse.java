package com.capstone.steam.common.dto;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/** Pagina stabile per il frontend (non si serializza direttamente il PageImpl di Spring Data). */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last) {

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast());
    }
}
