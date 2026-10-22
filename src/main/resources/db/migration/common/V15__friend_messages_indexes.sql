-- V15: índices del chat.
-- (pareja, id) sirve a la paginación por cursor y al último mensaje de cada conversación; sender_id acelera el borrado al anonimizar.
CREATE INDEX idx_friend_messages_pair_id ON friend_messages (user_low_id, user_high_id, id);
CREATE INDEX idx_friend_messages_sender ON friend_messages (sender_id);
