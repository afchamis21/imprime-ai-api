INSERT INTO order_status_lkup (
    code, name, description, status,
    create_dt, create_user, update_dt, update_user, guid
)
VALUES
    ('9.02', 'Refunded', 'Order has been refunded', 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System', gen_random_uuid())
ON CONFLICT (code) DO NOTHING;

INSERT INTO message_lkup (
    code, language_code, text, type, guid, status,
    create_dt, create_user, update_dt, update_user
)
VALUES
-- AUTH_403
('AUTH_403', 'en-US', 'Access denied.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
('AUTH_403', 'pt-BR', 'Acesso negado.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
('AUTH_403', 'es-ES', 'Acceso denegado.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),

-- SYS_400
('SYS_400', 'en-US', 'Validation error in request parameters.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
('SYS_400', 'pt-BR', 'Erro de validação nos parâmetros da requisição.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
('SYS_400', 'es-ES', 'Error de validación en los parámetros de la solicitud.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),

-- SYS_400_BODY
('SYS_400_BODY', 'en-US', 'Invalid or malformed JSON request body.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
('SYS_400_BODY', 'pt-BR', 'Corpo da requisição JSON inválido ou malformado.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
('SYS_400_BODY', 'es-ES', 'Cuerpo de solicitud JSON inválido o malformado.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),

-- SYS_409
('SYS_409', 'en-US', 'Conflict with current resource state.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
('SYS_409', 'pt-BR', 'Conflito com o estado atual do recurso.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
('SYS_409', 'es-ES', 'Conflicto con el estado actual del recurso.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),

-- SYS_501
('SYS_501', 'en-US', 'Feature not yet implemented: {}', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
('SYS_501', 'pt-BR', 'Funcionalidade ainda não implementada: {}', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
('SYS_501', 'es-ES', 'Funcionalidad aún no implementada: {}', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),

-- SYS_502
('SYS_502', 'en-US', 'Communication failure with external provider.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
('SYS_502', 'pt-BR', 'Falha de comunicação com o provedor externo.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
('SYS_502', 'es-ES', 'Error de comunicación con el proveedor externo.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),

-- ADR_020
('ADR_020', 'en-US', 'Address not found.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
('ADR_020', 'pt-BR', 'Endereço não encontrado.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
('ADR_020', 'es-ES', 'Dirección no encontrada.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),

-- COM_010
('COM_010', 'en-US', 'Company not found.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
('COM_010', 'pt-BR', 'Empresa não encontrada.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
('COM_010', 'es-ES', 'Empresa no encontrada.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),

-- ORD_004
('ORD_004', 'en-US', 'Order not found.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
('ORD_004', 'pt-BR', 'Pedido não encontrado.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
('ORD_004', 'es-ES', 'Pedido no encontrado.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),

-- ORD_005
('ORD_005', 'en-US', 'Invalid order status transition.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
('ORD_005', 'pt-BR', 'Transição de status do pedido inválida.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
('ORD_005', 'es-ES', 'Transición de estado del pedido inválida.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),

-- CHAT_001
('CHAT_001', 'en-US', 'Chat not found.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
('CHAT_001', 'pt-BR', 'Chat não encontrado.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
('CHAT_001', 'es-ES', 'Chat no encontrado.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),

-- CHAT_002
('CHAT_002', 'en-US', 'Offer value must be numeric.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
('CHAT_002', 'pt-BR', 'O valor da oferta deve ser numérico.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
('CHAT_002', 'es-ES', 'El valor de la oferta debe ser numérico.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),

-- CHAT_003
('CHAT_003', 'en-US', 'The referenced message is not an offer.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
('CHAT_003', 'pt-BR', 'A mensagem informada não é uma oferta.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
('CHAT_003', 'es-ES', 'El mensaje indicado no es una oferta.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),

-- MKR_001
('MKR_001', 'en-US', 'Maker not found.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
('MKR_001', 'pt-BR', 'Maker não encontrado.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
('MKR_001', 'es-ES', 'Maker no encontrado.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),

-- MKR_002
('MKR_002', 'en-US', 'Portfolio item not found.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
('MKR_002', 'pt-BR', 'Item de portfólio não encontrado.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
('MKR_002', 'es-ES', 'Elemento de portafolio no encontrado.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),

-- PRD_001
('PRD_001', 'en-US', 'Product not found.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
('PRD_001', 'pt-BR', 'Produto não encontrado.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
('PRD_001', 'es-ES', 'Producto no encontrado.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),

-- MDL_001
('MDL_001', 'en-US', '3D model not found.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
('MDL_001', 'pt-BR', 'Modelo 3D não encontrado.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
('MDL_001', 'es-ES', 'Modelo 3D no encontrado.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),

-- MDL_002
('MDL_002', 'en-US', 'This 3D model is not customizable.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
('MDL_002', 'pt-BR', 'Este modelo 3D não é customizável.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
('MDL_002', 'es-ES', 'Este modelo 3D no es personalizable.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),

-- MDL_003
('MDL_003', 'en-US', 'Purchased 3D model not found.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
('MDL_003', 'pt-BR', 'Compra de modelo 3D não encontrada.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
('MDL_003', 'es-ES', 'Compra de modelo 3D no encontrada.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),

-- REV_001
('REV_001', 'en-US', 'Review not found.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
('REV_001', 'pt-BR', 'Avaliação não encontrada.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
('REV_001', 'es-ES', 'Reseña no encontrada.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),

-- REV_002
('REV_002', 'en-US', 'You have already reviewed this item.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
('REV_002', 'pt-BR', 'Você já avaliou este item.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
('REV_002', 'es-ES', 'Ya has evaluado este elemento.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),

-- FAV_001
('FAV_001', 'en-US', 'Favorite not found.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
('FAV_001', 'pt-BR', 'Favorito não encontrado.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
('FAV_001', 'es-ES', 'Favorito no encontrado.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),

-- SUP_001
('SUP_001', 'en-US', 'Support ticket not found.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
('SUP_001', 'pt-BR', 'Chamado de suporte não encontrado.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
('SUP_001', 'es-ES', 'Ticket de soporte no encontrado.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),

-- PRT_001
('PRT_001', 'en-US', 'Printer not found.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
('PRT_001', 'pt-BR', 'Impressora não encontrada.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
('PRT_001', 'es-ES', 'Impresora no encontrada.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),

-- PRT_002
('PRT_002', 'en-US', 'Printer is currently busy.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
('PRT_002', 'pt-BR', 'A impressora está ocupada no momento.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
('PRT_002', 'es-ES', 'La impresora está ocupada actualmente.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),

-- FIL_001
('FIL_001', 'en-US', 'Filament or resin not found.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
('FIL_001', 'pt-BR', 'Filamento ou resina não encontrado.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
('FIL_001', 'es-ES', 'Filamento o resina no encontrado.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),

-- ARC_001
('ARC_001', 'en-US', 'Archive file not found.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
('ARC_001', 'pt-BR', 'Arquivo do acervo não encontrado.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
('ARC_001', 'es-ES', 'Archivo del repositorio no encontrado.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),

-- QUE_001
('QUE_001', 'en-US', 'Print queue job not found.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
('QUE_001', 'pt-BR', 'Job da fila de impressão não encontrado.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
('QUE_001', 'es-ES', 'Trabajo en cola de impresión no encontrado.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),

-- PRJ_001
('PRJ_001', 'en-US', 'Project not found.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
('PRJ_001', 'pt-BR', 'Projeto não encontrado.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
('PRJ_001', 'es-ES', 'Proyecto no encontrado.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),

-- PRJ_002
('PRJ_002', 'en-US', 'BOM item not found.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
('PRJ_002', 'pt-BR', 'Item da lista de materiais (BOM) não encontrado.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
('PRJ_002', 'es-ES', 'Elemento de la lista de materiales (BOM) no encontrado.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),

-- PRJ_003
('PRJ_003', 'en-US', 'All project plates are already completed.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
('PRJ_003', 'pt-BR', 'Todas as mesas do projeto já foram concluídas.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
('PRJ_003', 'es-ES', 'Todas las bandejas del proyecto ya están completadas.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),

-- SRC_001
('SRC_001', 'en-US', 'Model source not found.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
('SRC_001', 'pt-BR', 'Fonte de modelos não encontrada.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
('SRC_001', 'es-ES', 'Fuente de modelos no encontrada.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),

-- SRC_002
('SRC_002', 'en-US', 'Failed to synchronize external model source.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
('SRC_002', 'pt-BR', 'Falha ao sincronizar a fonte externa de modelos.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
('SRC_002', 'es-ES', 'Error al sincronizar la fuente externa de modelos.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),

-- FIN_001
('FIN_001', 'en-US', 'Insufficient available balance for payout.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
('FIN_001', 'pt-BR', 'Saldo disponível insuficiente para saque.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
('FIN_001', 'es-ES', 'Saldo disponible insuficiente para retiro.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System')
ON CONFLICT (code, language_code) DO NOTHING;
