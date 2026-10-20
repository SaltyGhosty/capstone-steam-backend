-- V12.1 (H2, solo tests): equivalente de db/migration/postgresql/V12_1
-- H2 no admite índices por expresión: se usa una columna generada con el usuario en minúsculas y un UNIQUE sobre ella.
ALTER TABLE users ADD COLUMN username_lower VARCHAR(50) GENERATED ALWAYS AS (LOWER(username));
CREATE UNIQUE INDEX uk_users_username_lower ON users (username_lower);
