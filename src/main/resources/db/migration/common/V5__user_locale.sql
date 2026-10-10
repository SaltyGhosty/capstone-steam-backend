-- V5: idioma preferido de cada usuario (para los correos).
-- Los usuarios existentes quedan en 'es' gracias al DEFAULT.
ALTER TABLE users ADD COLUMN locale VARCHAR(5) DEFAULT 'es' NOT NULL;

ALTER TABLE users ADD CONSTRAINT ck_users_locale CHECK (locale IN ('es', 'en', 'it'));
