INSERT INTO message_lkup (
    code, language_code, text, type, guid, status,
    create_dt, create_user, update_dt, update_user
)
VALUES
    -- ADM_001
    ('ADM_001', 'en-US', 'Administrator role is required to access this resource.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
    ('ADM_001', 'pt-BR', 'Perfil de administrador é obrigatório para acessar este recurso.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
    ('ADM_001', 'es-ES', 'Se requiere rol de administrador para acceder a este recurso.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),

    -- ADM_002
    ('ADM_002', 'en-US', 'You cannot delete or suspend your own administrator account.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
    ('ADM_002', 'pt-BR', 'Você não pode excluir ou suspender sua própria conta de administrador.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
    ('ADM_002', 'es-ES', 'No puedes eliminar o suspender tu propia cuenta de administrador.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),

    -- ADM_003
    ('ADM_003', 'en-US', 'Cannot deactivate or downgrade the last active administrator.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
    ('ADM_003', 'pt-BR', 'Não é possível desativar ou rebaixar o último administrador ativo.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
    ('ADM_003', 'es-ES', 'No se puede desactivar o degradar al último administrador activo.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),

    -- ADM_004
    ('ADM_004', 'en-US', 'A reason is required for this administrative action.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
    ('ADM_004', 'pt-BR', 'Um motivo é obrigatório para executar esta ação administrativa.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
    ('ADM_004', 'es-ES', 'Se requiere un motivo para realizar esta acción administrativa.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),

    -- USR_024
    ('USR_024', 'en-US', 'User not found.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
    ('USR_024', 'pt-BR', 'Usuário não encontrado.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
    ('USR_024', 'es-ES', 'Usuario no encontrado.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),

    -- REF_001
    ('REF_001', 'en-US', 'Refund request not found.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
    ('REF_001', 'pt-BR', 'Solicitação de reembolso não encontrada.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
    ('REF_001', 'es-ES', 'Solicitud de reembolso no encontrada.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),

    -- REF_002
    ('REF_002', 'en-US', 'Refund amount exceeds the eligible order total or is invalid.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
    ('REF_002', 'pt-BR', 'O valor do reembolso é inválido ou excede o total elegível do pedido.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
    ('REF_002', 'es-ES', 'El monto del reembolso es inválido o supera el total elegible del pedido.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),

    -- REF_003
    ('REF_003', 'en-US', 'This refund request has already been processed.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
    ('REF_003', 'pt-BR', 'Esta solicitação de reembolso já foi processada.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
    ('REF_003', 'es-ES', 'Esta solicitud de reembolso ya fue procesada.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),

    -- FIN_002
    ('FIN_002', 'en-US', 'Payout record not found.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
    ('FIN_002', 'pt-BR', 'Registro de repasse não encontrado.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
    ('FIN_002', 'es-ES', 'Registro de pago no encontrado.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),

    -- FIN_003
    ('FIN_003', 'en-US', 'Payout cannot transition from its current status.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
    ('FIN_003', 'pt-BR', 'O repasse não pode ser alterado a partir do status atual.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
    ('FIN_003', 'es-ES', 'El pago no puede cambiar desde su estado actual.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),

    -- AUD_001
    ('AUD_001', 'en-US', 'Audit log record not found.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
    ('AUD_001', 'pt-BR', 'Registro de auditoria não encontrado.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System'),
    ('AUD_001', 'es-ES', 'Registro de auditoría no encontrado.', 'E', gen_random_uuid(), 'A', CURRENT_TIMESTAMP, 'System', CURRENT_TIMESTAMP, 'System')
ON CONFLICT (code, language_code) DO NOTHING;
