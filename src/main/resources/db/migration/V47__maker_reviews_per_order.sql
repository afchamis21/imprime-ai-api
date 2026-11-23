DROP INDEX IF EXISTS uq_review_active_author_target;

CREATE UNIQUE INDEX IF NOT EXISTS uq_review_active_author_target_no_order
    ON review (author_user_id, target_type, target_id)
    WHERE status = 'A' AND order_id IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_review_active_author_order_target
    ON review (author_user_id, target_type, target_id, order_id)
    WHERE status = 'A' AND order_id IS NOT NULL;

INSERT INTO message_lkup (
    code, language_code, text, type, guid, status,
    create_dt, create_user, update_dt, update_user
)
VALUES
    ('REV_003', 'en-US', 'You can only review a Maker after completing and receiving an order from them.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
    ('REV_003', 'pt-BR', 'Você só pode avaliar um Maker após concluir e receber um pedido com ele.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
    ('REV_003', 'es-ES', 'Solo puedes evaluar a un Maker después de completar y recibir un pedido con él.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System')
ON CONFLICT (code, language_code) DO NOTHING;
