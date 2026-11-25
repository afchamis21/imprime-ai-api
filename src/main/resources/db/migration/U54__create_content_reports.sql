-- ============================================================================
-- U54: Rollback da V54 (Sistema de denúncias para Produtos, Modelos 3D e Makers)
-- ============================================================================

DELETE FROM message_lkup
WHERE code IN ('RPT_001', 'RPT_002', 'RPT_003', 'RPT_004');

DROP TABLE IF EXISTS content_report;
