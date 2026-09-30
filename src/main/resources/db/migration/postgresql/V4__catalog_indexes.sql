-- =====================================================================
-- V4 (PostgreSQL): índices de rendimiento para el catálogo
-- La variante H2 está en db/migration/h2 (sin pg_trgm ni índices parciales)
-- =====================================================================

-- Búsqueda por título con LIKE '%texto%' (case-insensitive) mediante trigramas
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX idx_games_title_trgm ON games USING gin (lower(title) gin_trgm_ops);

-- Filtros y ordenaciones del catálogo
CREATE INDEX idx_games_status_release ON games (status, release_date DESC);
CREATE INDEX idx_games_status_price ON games (status, price);
CREATE INDEX idx_games_developer ON games (developer_id);

-- "Más vendidos": índice parcial que solo incluye juegos publicados
CREATE INDEX idx_games_published_sales ON games (sales_count DESC) WHERE status = 'PUBLISHED';

-- Filtro por género (la PK cubre game_id -> genre_id; este cubre la dirección inversa)
CREATE INDEX idx_game_genres_genre ON game_genres (genre_id, game_id);

-- Reseñas de un juego ordenadas por fecha
CREATE INDEX idx_reviews_game_created ON reviews (game_id, created_at DESC);

-- Historial de pedidos del usuario y claves foráneas usadas en joins
CREATE INDEX idx_orders_user_created ON orders (user_id, created_at DESC);
CREATE INDEX idx_order_items_game ON order_items (game_id);
CREATE INDEX idx_payments_order ON payments (order_id);
CREATE INDEX idx_library_game ON library_entries (game_id);
