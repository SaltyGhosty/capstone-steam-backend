-- V12.1: el usuario es único sin distinguir mayúsculas también en la base de datos (hasta ahora solo lo
-- comprobaba el código con existsByUsernameIgnoreCase). Si dos peticiones simultáneas se saltan la
-- comprobación, el índice lo frena y el conflicto acaba en 409.
CREATE UNIQUE INDEX uk_users_username_lower ON users (lower(username));
