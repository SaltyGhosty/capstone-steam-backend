-- =====================================================================
-- V4 (H2, solo tests): equivalente de db/migration/postgresql/V4
-- H2 no soporta pg_trgm, índices GIN ni índices parciales: solo B-tree
-- =====================================================================

CREATE INDEX idx_games_title ON games (title);
CREATE INDEX idx_games_status_release ON games (status, release_date DESC);
CREATE INDEX idx_games_status_price ON games (status, price);
CREATE INDEX idx_games_developer ON games (developer_id);
CREATE INDEX idx_games_status_sales ON games (status, sales_count DESC);
CREATE INDEX idx_game_genres_genre ON game_genres (genre_id, game_id);
CREATE INDEX idx_reviews_game_created ON reviews (game_id, created_at DESC);
CREATE INDEX idx_orders_user_created ON orders (user_id, created_at DESC);
CREATE INDEX idx_order_items_game ON order_items (game_id);
CREATE INDEX idx_payments_order ON payments (order_id);
CREATE INDEX idx_library_game ON library_entries (game_id);
