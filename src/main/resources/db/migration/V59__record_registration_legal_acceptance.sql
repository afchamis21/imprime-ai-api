CREATE TABLE user_legal_acceptance
(
    user_id BIGINT NOT NULL REFERENCES users (user_id),
    legal_document_id BIGINT NOT NULL REFERENCES legal_document (legal_document_id),
    event_type VARCHAR(32) NOT NULL CHECK (event_type = 'REGISTRATION_ACCEPTANCE'),
    accepted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, legal_document_id)
);

INSERT INTO message_lkup (code, language_code, text, type, guid, status, create_dt, create_user)
VALUES
    ('LEG_001', 'pt-BR', 'Aceite os Termos de Uso e confirme a leitura da Política de Privacidade.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System'),
    ('LEG_001', 'en-US', 'Accept the Terms of Use and acknowledge the Privacy Policy.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System'),
    ('LEG_001', 'es-ES', 'Acepta los Términos de uso y confirma la lectura de la Política de privacidad.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System'),
    ('LEG_002', 'pt-BR', 'As versões em vigor mudaram ou estão indisponíveis. Revise os documentos e tente novamente.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System'),
    ('LEG_002', 'en-US', 'The effective versions changed or are unavailable. Review the documents and try again.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System'),
    ('LEG_002', 'es-ES', 'Las versiones vigentes cambiaron o no están disponibles. Revisa los documentos y vuelve a intentarlo.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System');
