INSERT INTO message_lkup (CODE, LANGUAGE_CODE, TEXT, TYPE, GUID, STATUS,
                          CREATE_DT, CREATE_USER, UPDATE_DT, UPDATE_USER)
VALUES ('PHO_001', 'en-US', 'Invalid phone format.', 'E', uuid(), 'A', CURRENT_TIMESTAMP, 'Andre', CURRENT_TIMESTAMP,
        'Andre'),
       ('PHO_001', 'pt-BR', 'Formato de telefone inválido.', 'E', uuid(), 'A', CURRENT_TIMESTAMP, 'Andre',
        CURRENT_TIMESTAMP, 'Andre'),
       ('PHO_001', 'es-ES', 'Formato de teléfono inválido.', 'E', uuid(), 'A', CURRENT_TIMESTAMP, 'Andre',
        CURRENT_TIMESTAMP, 'Andre');