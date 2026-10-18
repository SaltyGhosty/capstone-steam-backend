-- V7: enlace de cada juego con su ficha de la tienda de Steam (AppID) y última sincronización del precio.
-- Los juegos sin AppID (creados a mano por un estudio) no se sincronizan.
ALTER TABLE games ADD COLUMN steam_app_id INTEGER;
ALTER TABLE games ADD COLUMN price_synced_at TIMESTAMP WITH TIME ZONE;
CREATE UNIQUE INDEX ux_games_steam_app_id ON games (steam_app_id);
