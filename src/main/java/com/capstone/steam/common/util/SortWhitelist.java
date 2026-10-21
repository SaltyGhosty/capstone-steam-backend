package com.capstone.steam.common.util;

import java.util.Set;
import java.util.TreeSet;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.capstone.steam.common.exception.BadRequestException;

/**
 * Comprueba el {@code sort} que llega del cliente contra una lista de campos permitidos.
 * Sin esto, un campo inventado o una expresión ({@code sort=1;delete…}) llegaba a Spring Data
 * y acababa en 500; ahora es un 400 con los valores válidos.
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
