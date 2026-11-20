DROP TABLE IF EXISTS model_source_integration;
DROP TABLE IF EXISTS filament_stock_movement;
DROP TABLE IF EXISTS print_queue_item;
DROP TABLE IF EXISTS maker_archive_file;
DROP TABLE IF EXISTS maker_project_bom_item;
DROP TABLE IF EXISTS maker_project;
DROP TABLE IF EXISTS printer_ams_slot;

DROP INDEX IF EXISTS idx_filament_company_status;
DROP INDEX IF EXISTS idx_asset_company_status;

ALTER TABLE filament
    DROP COLUMN IF EXISTS version,
    DROP COLUMN IF EXISTS notes,
    DROP COLUMN IF EXISTS spool_location,
    DROP COLUMN IF EXISTS humidity_percent,
    DROP COLUMN IF EXISTS low_stock_threshold_grams,
    DROP COLUMN IF EXISTS diameter_mm,
    DROP COLUMN IF EXISTS cost_per_kg,
    DROP COLUMN IF EXISTS initial_weight_grams,
    DROP COLUMN IF EXISTS color_hex,
    DROP COLUMN IF EXISTS color_name,
    DROP COLUMN IF EXISTS material,
    DROP COLUMN IF EXISTS brand,
    DROP COLUMN IF EXISTS name;

ALTER TABLE asset
    DROP COLUMN IF EXISTS telemetry,
    DROP COLUMN IF EXISTS config,
    DROP COLUMN IF EXISTS api_key_prefix,
    DROP COLUMN IF EXISTS api_key_hash,
    DROP COLUMN IF EXISTS printer_status,
    DROP COLUMN IF EXISTS serial_number,
    DROP COLUMN IF EXISTS ip_address,
    DROP COLUMN IF EXISTS power_watts,
    DROP COLUMN IF EXISTS nozzle_diameter_mm,
    DROP COLUMN IF EXISTS tray_height,
    DROP COLUMN IF EXISTS name;
