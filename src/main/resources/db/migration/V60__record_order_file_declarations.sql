CREATE TABLE order_file_declaration
(
    order_id BIGINT NOT NULL REFERENCES orders (order_id),
    file_asset_id BIGINT NOT NULL REFERENCES file_asset (file_asset_id),
    buyer_id BIGINT NOT NULL REFERENCES users (user_id),
    declaration_document_id BIGINT NOT NULL REFERENCES legal_document (legal_document_id),
    prohibited_items_document_id BIGINT NOT NULL REFERENCES legal_document (legal_document_id),
    content_sha256 CHAR(64) NOT NULL,
    declared_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (order_id, file_asset_id)
);

INSERT INTO message_lkup (code, language_code, text, type, guid, status, create_dt, create_user)
VALUES
    ('LEG_003', 'pt-BR', 'Confirme a declaração de direitos e conformidade antes de enviar o modelo.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System'),
    ('LEG_003', 'en-US', 'Confirm the rights and compliance declaration before submitting the model.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System'),
    ('LEG_003', 'es-ES', 'Confirma la declaración de derechos y cumplimiento antes de enviar el modelo.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System'),
    ('LEG_004', 'pt-BR', 'O modelo tem estrutura inválida ou excede os limites de envio.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System'),
    ('LEG_004', 'en-US', 'The model has an invalid structure or exceeds upload limits.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System'),
    ('LEG_004', 'es-ES', 'El modelo tiene una estructura no válida o supera los límites de envío.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System');
