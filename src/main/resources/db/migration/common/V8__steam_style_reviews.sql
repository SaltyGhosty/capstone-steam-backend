-- V8: reseñas estilo Steam. Cada reseña es positiva o negativa (recommended) y guarda su idioma;
-- desaparecen las estrellas y la suma de puntuaciones.

-- Idioma de la reseña: las existentes toman el idioma de su autor.
ALTER TABLE reviews ADD COLUMN language VARCHAR(5) DEFAULT 'es' NOT NULL;
UPDATE reviews SET language = (SELECT u.locale FROM users u WHERE u.id = reviews.user_id);
ALTER TABLE reviews ADD CONSTRAINT ck_reviews_language CHECK (language IN ('es', 'en', 'it'));

-- Fuera las estrellas.
ALTER TABLE reviews DROP CONSTRAINT ck_reviews_rating;
ALTER TABLE reviews DROP COLUMN rating;

-- Contadores del juego: review_count (antes rating_count) y positive_count.
ALTER TABLE games DROP CONSTRAINT ck_games_counters;
ALTER TABLE games DROP COLUMN rating_sum;
ALTER TABLE games RENAME COLUMN rating_count TO review_count;
ALTER TABLE games ADD COLUMN positive_count INTEGER DEFAULT 0 NOT NULL;

UPDATE games SET
    review_count = (SELECT COUNT(*) FROM reviews r WHERE r.game_id = games.id),
    positive_count = (SELECT COUNT(*) FROM reviews r WHERE r.game_id = games.id AND r.recommended = TRUE);

ALTER TABLE games ADD CONSTRAINT ck_games_counters
    CHECK (sales_count >= 0 AND review_count >= 0 AND positive_count >= 0 AND positive_count <= review_count);

-- Lista de reseñas de un juego filtrada por idioma, la más reciente primero.
CREATE INDEX idx_reviews_game_language_created ON reviews (game_id, language, created_at DESC);
