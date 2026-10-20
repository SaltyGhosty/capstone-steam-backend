package com.capstone.steam.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * ¿Tiene la cuenta algo que perder? Una cuenta sin verificar solo se puede borrar para liberar su
 * usuario si no tiene pedidos, biblioteca, carrito, wishlist, reseñas, juegos, amistades ni sesiones.
 */
@Component
public class AccountDataCheck {

    private static final String SQL = """
            SELECT (SELECT COUNT(*) FROM orders WHERE user_id = ?)
                 + (SELECT COUNT(*) FROM library_entries WHERE user_id = ?)
                 + (SELECT COUNT(*) FROM cart_items WHERE user_id = ?)
                 + (SELECT COUNT(*) FROM wishlist_items WHERE user_id = ?)
                 + (SELECT COUNT(*) FROM reviews WHERE user_id = ?)
                 + (SELECT COUNT(*) FROM games WHERE developer_id = ?)
                 + (SELECT COUNT(*) FROM friendships WHERE requester_id = ? OR addressee_id = ?)
                 + (SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ?)""";

    private final JdbcTemplate jdbc;

    public AccountDataCheck(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean hasData(Long userId) {
        Long total = jdbc.queryForObject(SQL, Long.class, userId, userId, userId, userId, userId, userId, userId,
                userId, userId);
        return total != null && total > 0;
    }
}
