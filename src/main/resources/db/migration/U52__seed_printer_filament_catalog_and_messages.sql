-- ============================================================================
-- U52: Rollback do Seed de Catálogos, Backfill de Makers e Mensagens i18n
-- ============================================================================

DELETE FROM message_lkup
WHERE code IN ('PRT_CAT_001', 'PRT_CAT_002', 'FIL_CAT_001', 'FIL_CAT_002', 'CAT_001');

DELETE FROM filament_catalog
WHERE create_user = 'FLYWAY_V52';

UPDATE company
SET primary_printer_catalog_id = NULL
WHERE primary_printer_catalog_id IN (
    SELECT printer_catalog_id
    FROM printer_catalog
    WHERE create_user = 'FLYWAY_V52'
);

DELETE FROM asset
WHERE create_user = 'FLYWAY_V52_BACKFILL';

UPDATE asset
SET printer_catalog_id = NULL
WHERE printer_catalog_id IN (
    SELECT printer_catalog_id
    FROM printer_catalog
    WHERE create_user = 'FLYWAY_V52'
);

DELETE FROM printer_catalog_material
WHERE printer_catalog_id IN (
    SELECT printer_catalog_id
    FROM printer_catalog
    WHERE create_user = 'FLYWAY_V52'
);

DELETE FROM printer_catalog
WHERE create_user = 'FLYWAY_V52';
