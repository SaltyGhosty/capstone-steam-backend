-- V6: descripción de los juegos en inglés e italiano. La de siempre (description) es la española
-- y hace de respaldo cuando falta la traducción.
ALTER TABLE games ADD COLUMN description_en VARCHAR(10000);
ALTER TABLE games ADD COLUMN description_it VARCHAR(10000);
