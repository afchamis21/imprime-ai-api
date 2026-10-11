ALTER TABLE MESSAGE_LKUP
    ADD LANGUAGE_CODE VARCHAR(5) DEFAULT 'en-US' NOT NULL;

ALTER TABLE MESSAGE_LKUP
    ADD CONSTRAINT UQ_MESSAGE_LKUP_CODE_LANG
        UNIQUE (CODE, LANGUAGE_CODE);

ALTER TABLE MESSAGE_LKUP DROP CONSTRAINT UQ_MESSAGE_LKUP_CODE;

INSERT INTO message_lkup (code, language_code, text, type, guid, status, create_dt, create_user, update_dt, update_user)
VALUES ('SYS_500', 'pt-BR', 'Erro Interno do Servidor', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'Andre', CURRENT_TIMESTAMP, 'Andre');

INSERT INTO message_lkup (code, language_code, text, type, guid, status, create_dt, create_user, update_dt, update_user)
VALUES ('AUTH_401', 'pt-BR', 'Não autorizado', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'Andre', CURRENT_TIMESTAMP, 'Andre');
