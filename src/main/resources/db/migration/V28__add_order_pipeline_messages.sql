INSERT INTO message_lkup (
    CODE, LANGUAGE_CODE, TEXT, TYPE, GUID, STATUS,
    CREATE_DT, CREATE_USER, UPDATE_DT, UPDATE_USER
)
VALUES
-- ADR_019
('ADR_019', 'en-US', 'The address is not active.', 'E', uuid(), 'A', CURRENT_TIMESTAMP, 'Andre', CURRENT_TIMESTAMP, 'Andre'),
('ADR_019', 'pt-BR', 'O endereço não está ativo.', 'E', uuid(), 'A', CURRENT_TIMESTAMP, 'Andre', CURRENT_TIMESTAMP, 'Andre'),
('ADR_019', 'es-ES', 'La dirección no está activa.', 'E', uuid(), 'A', CURRENT_TIMESTAMP, 'Andre', CURRENT_TIMESTAMP, 'Andre'),

-- FL_001
('FL_001', 'en-US', 'The file is empty.', 'E', uuid(), 'A', CURRENT_TIMESTAMP, 'Andre', CURRENT_TIMESTAMP, 'Andre'),
('FL_001', 'pt-BR', 'O arquivo está vazio.', 'E', uuid(), 'A', CURRENT_TIMESTAMP, 'Andre', CURRENT_TIMESTAMP, 'Andre'),
('FL_001', 'es-ES', 'El archivo está vacío.', 'E', uuid(), 'A', CURRENT_TIMESTAMP, 'Andre', CURRENT_TIMESTAMP, 'Andre'),

-- FL_002
('FL_002', 'en-US', 'The file name is missing.', 'E', uuid(), 'A', CURRENT_TIMESTAMP, 'Andre', CURRENT_TIMESTAMP, 'Andre'),
('FL_002', 'pt-BR', 'O nome do arquivo está ausente.', 'E', uuid(), 'A', CURRENT_TIMESTAMP, 'Andre', CURRENT_TIMESTAMP, 'Andre'),
('FL_002', 'es-ES', 'Falta el nombre del archivo.', 'E', uuid(), 'A', CURRENT_TIMESTAMP, 'Andre', CURRENT_TIMESTAMP, 'Andre'),

-- FL_003
('FL_003', 'en-US', 'The file extension is not supported. Supported extensions are: [{}]', 'E', uuid(), 'A', CURRENT_TIMESTAMP, 'Andre', CURRENT_TIMESTAMP, 'Andre'),
('FL_003', 'pt-BR', 'A extensão do arquivo não é suportada. As extensões suportadas são: [{}]', 'E', uuid(), 'A', CURRENT_TIMESTAMP, 'Andre', CURRENT_TIMESTAMP, 'Andre'),
('FL_003', 'es-ES', 'La extensión del archivo no es compatible. Las extensiones compatibles son: [{}]', 'E', uuid(), 'A', CURRENT_TIMESTAMP, 'Andre', CURRENT_TIMESTAMP, 'Andre'),

-- ORD_001
('ORD_001', 'en-US', 'The address GUID is missing.', 'E', uuid(), 'A', CURRENT_TIMESTAMP, 'Andre', CURRENT_TIMESTAMP, 'Andre'),
('ORD_001', 'pt-BR', 'O GUID do endereço está ausente.', 'E', uuid(), 'A', CURRENT_TIMESTAMP, 'Andre', CURRENT_TIMESTAMP, 'Andre'),
('ORD_001', 'es-ES', 'Falta el GUID de la dirección.', 'E', uuid(), 'A', CURRENT_TIMESTAMP, 'Andre', CURRENT_TIMESTAMP, 'Andre'),

-- ORD_002
('ORD_002', 'en-US', 'The address GUID is invalid.', 'E', uuid(), 'A', CURRENT_TIMESTAMP, 'Andre', CURRENT_TIMESTAMP, 'Andre'),
('ORD_002', 'pt-BR', 'O GUID do endereço é inválido.', 'E', uuid(), 'A', CURRENT_TIMESTAMP, 'Andre', CURRENT_TIMESTAMP, 'Andre'),
('ORD_002', 'es-ES', 'El GUID de la dirección no es válido.', 'E', uuid(), 'A', CURRENT_TIMESTAMP, 'Andre', CURRENT_TIMESTAMP, 'Andre');

-- ORD_003
('ORD_003', 'en-US', 'Error loading the list of Makers. Please try again.', 'E', uuid(), 'A', CURRENT_TIMESTAMP, 'Andre', CURRENT_TIMESTAMP, 'Andre'),
('ORD_003', 'pt-BR', 'Erro ao carregar a lista de Makers. Tente novamente.', 'E', uuid(), 'A', CURRENT_TIMESTAMP, 'Andre', CURRENT_TIMESTAMP, 'Andre'),
('ORD_003', 'es-ES', 'Error al cargar la lista de Makers. Inténtelo de nuevo.', 'E', uuid(), 'A', CURRENT_TIMESTAMP, 'Andre', CURRENT_TIMESTAMP, 'Andre'),