INSERT INTO message_lkup (
    CODE, LANGUAGE_CODE, TEXT, TYPE, GUID, STATUS,
    CREATE_DT, CREATE_USER, UPDATE_DT, UPDATE_USER
)
VALUES
-- COM_008
('COM_008', 'en-US', 'A company with this name already exists.', 'E', uuid(), 'A', CURRENT_TIMESTAMP, 'Andre', CURRENT_TIMESTAMP, 'Andre'),
('COM_008', 'pt-BR', 'Já existe uma empresa com este nome.', 'E', uuid(), 'A', CURRENT_TIMESTAMP, 'Andre', CURRENT_TIMESTAMP, 'Andre'),
('COM_008', 'es-ES', 'Ya existe una empresa con este nombre.', 'E', uuid(), 'A', CURRENT_TIMESTAMP, 'Andre', CURRENT_TIMESTAMP, 'Andre'),

-- COM_009
('COM_009', 'en-US', 'A company with this document already exists.', 'E', uuid(), 'A', CURRENT_TIMESTAMP, 'Andre', CURRENT_TIMESTAMP, 'Andre'),
('COM_009', 'pt-BR', 'Já existe uma empresa com este documento.', 'E', uuid(), 'A', CURRENT_TIMESTAMP, 'Andre', CURRENT_TIMESTAMP, 'Andre'),
('COM_009', 'es-ES', 'Ya existe una empresa con este documento.', 'E', uuid(), 'A', CURRENT_TIMESTAMP, 'Andre', CURRENT_TIMESTAMP, 'Andre'),

-- USR_021
('USR_021', 'en-US', 'A user with this email already exists.', 'E', uuid(), 'A', CURRENT_TIMESTAMP, 'Andre', CURRENT_TIMESTAMP, 'Andre'),
('USR_021', 'pt-BR', 'Já existe um usuário com este e-mail.', 'E', uuid(), 'A', CURRENT_TIMESTAMP, 'Andre', CURRENT_TIMESTAMP, 'Andre'),
('USR_021', 'es-ES', 'Ya existe un usuario con este correo electrónico.', 'E', uuid(), 'A', CURRENT_TIMESTAMP, 'Andre', CURRENT_TIMESTAMP, 'Andre'),

-- USR_022
('USR_022', 'en-US', 'A user with this document already exists.', 'E', uuid(), 'A', CURRENT_TIMESTAMP, 'Andre', CURRENT_TIMESTAMP, 'Andre'),
('USR_022', 'pt-BR', 'Já existe um usuário com este documento.', 'E', uuid(), 'A', CURRENT_TIMESTAMP, 'Andre', CURRENT_TIMESTAMP, 'Andre'),
('USR_022', 'es-ES', 'Ya existe un usuario con este documento.', 'E', uuid(), 'A', CURRENT_TIMESTAMP, 'Andre', CURRENT_TIMESTAMP, 'Andre');