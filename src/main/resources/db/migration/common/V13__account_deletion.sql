-- V13: eliminar la cuenta con un plazo de margen. deletion_scheduled_at es el momento en que se anonimiza;
-- deleted_at marca la cuenta ya anonimizada. Las cuentas que ya existen quedan sin baja programada.
ALTER TABLE users ADD COLUMN deletion_scheduled_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE users ADD COLUMN deletion_requested_by VARCHAR(10);
ALTER TABLE users ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE users ADD CONSTRAINT ck_users_deletion_requested_by CHECK (deletion_requested_by IN ('SELF', 'ADMIN'));
CREATE INDEX idx_users_deletion_scheduled_at ON users (deletion_scheduled_at);
