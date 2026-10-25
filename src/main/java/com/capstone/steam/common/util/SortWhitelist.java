package com.capstone.steam.common.util;

import java.util.Set;
import java.util.TreeSet;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.capstone.steam.common.exception.BadRequestException;

/**
 * Verifica il {@code sort} che arriva dal client rispetto a una lista di campi consentiti.
 * Senza questo, un campo inventato o un'espressione ({@code sort=1;delete…}) arrivava a Spring Data
 * e finiva in 500; ora è un 400 con i valori validi.
 */
public final class SortWhitelist {

    private SortWhitelist() {
    }

    public static Pageable require(Pageable pageable, Set<String> allowed) {
        for (Sort.Order order : pageable.getSort()) {
            if (!allowed.contains(order.getProperty())) {
                throw new BadRequestException("catalog.invalidSort", order.getProperty(),
                        String.join(", ", new TreeSet<>(allowed)));
            }
        }
        return pageable;
    }
}
