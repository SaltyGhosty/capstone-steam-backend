-- V11: la pareja low/high debe ser exactamente la del solicitante y el destinatario.
-- V9 solo exigía low < high; así la base de datos impide una fila con un par que no es el de la solicitud.
ALTER TABLE friendships ADD CONSTRAINT ck_friendships_pair CHECK (
    (user_low_id = requester_id AND user_high_id = addressee_id)
    OR (user_low_id = addressee_id AND user_high_id = requester_id)
);
